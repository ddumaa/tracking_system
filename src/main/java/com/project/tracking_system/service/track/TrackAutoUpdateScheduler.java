package com.project.tracking_system.service.track;

import com.project.tracking_system.entity.PostalServiceType;
import com.project.tracking_system.entity.TrackParcel;
import com.project.tracking_system.model.subscription.FeatureKey;
import com.project.tracking_system.repository.TrackParcelRepository;
import com.project.tracking_system.repository.UserSubscriptionRepository;
import com.project.tracking_system.service.SubscriptionService;
import com.project.tracking_system.service.user.UserService;
import com.project.tracking_system.dto.TrackingResultAdd;
import com.project.tracking_system.service.belpost.BelPostTrackQueueService;
import com.project.tracking_system.service.belpost.QueuedTrack;
import com.project.tracking_system.service.track.TrackMeta;
import com.project.tracking_system.service.track.TrackUpdateService;
import com.project.tracking_system.service.track.TypeDefinitionTrackPostService;
import com.project.tracking_system.service.admin.ApplicationSettingsService;
import java.util.ArrayList;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;

/**
 * Планировщик автоматического обновления треков.
 * <p>
 * Выполняет обход пользователей, для которых в тарифе
 * разрешено автоматическое обновление, и обновляет их активные треки.
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TrackAutoUpdateScheduler {

    private final UserSubscriptionRepository userSubscriptionRepository;
    private final TrackParcelRepository trackParcelRepository;
    private final SubscriptionService subscriptionService;
    private final UserService userService;
    private final TrackUpdateService trackUpdateService;
    private final BelPostTrackQueueService belPostTrackQueueService;
    private final TypeDefinitionTrackPostService typeDefinitionTrackPostService;
    private final ApplicationSettingsService applicationSettingsService;

    /**
     * Запускает автообновление треков для всех подходящих пользователей.
     *
     * <p>Метод не оборачивается в транзакцию, чтобы не держать
     * одну длительную сессию на всех пользователей. Каждое обновление
     * выполняется в собственной транзакции внутри {@link #updateUserTracks(Long)}.</p>
     */
    public void updateAllUsersTracks() {
        List<Long> userIds = userSubscriptionRepository.findUserIdsByFeature(FeatureKey.AUTO_UPDATE);
        if (userIds.isEmpty()) {
            log.info("Нет пользователей с автообновлением треков");
            return;
        }

        for (Long userId : userIds) {
            if (userService.isAutoUpdateEnabled(userId)) {
                try {
                    updateUserTracks(userId);
                } catch (Exception e) {
                    log.error("Не удалось автообновить треки для userId={}", userId, e);
                }
            }
        }
    }

    /**
     * Обновляет треки для одного пользователя.
     *
     * <p>Все операции выполняются в отдельной транзакции,
     * чтобы ошибки конкретного пользователя не влияли на остальных
     * и не блокировали долгосрочную транзакцию.</p>
     *
     * @param userId идентификатор пользователя
     */
    @Transactional
    protected void updateUserTracks(Long userId) {
        List<TrackParcel> parcels = trackParcelRepository.findByUserId(userId);
        int interval = applicationSettingsService.getTrackUpdateIntervalHours();
        ZonedDateTime threshold = ZonedDateTime.now(ZoneOffset.UTC).minusHours(interval);

        List<TrackParcel> active = parcels.stream()
                .filter(p -> !p.getStatus().isFinal())
                .filter(p -> p.getLastUpdate() == null || p.getLastUpdate().isBefore(threshold))
                .toList();

        if (active.isEmpty()) {
            return;
        }

        int allowed = subscriptionService.canUpdateTracks(userId, active.size());
        if (allowed <= 0) {
            log.debug("Лимит автообновлений исчерпан для userId={}", userId);
            return;
        }

        List<TrackParcel> limited = active.subList(0, Math.min(allowed, active.size()));

        List<TrackMeta> others = new ArrayList<>();
        List<QueuedTrack> belpostTracks = new ArrayList<>();
        long batchId = System.currentTimeMillis();

        for (TrackParcel parcel : limited) {
            PostalServiceType type = parcel.getDeliveryHistory() != null
                    ? parcel.getDeliveryHistory().getPostalService()
                    : typeDefinitionTrackPostService.detectPostalService(parcel.getNumber());

            if (type == PostalServiceType.BELPOST) {
                belpostTracks.add(new QueuedTrack(
                        parcel.getNumber(),
                        userId,
                        parcel.getStore().getId(),
                        "AUTO",
                        batchId
                ));
            } else {
                others.add(new TrackMeta(
                        parcel.getNumber(),
                        parcel.getStore().getId(),
                        null,
                        true,
                        type
                ));
            }
        }

        if (!belpostTracks.isEmpty()) {
            belPostTrackQueueService.enqueue(belpostTracks);
            log.info("В очередь Белпочты добавлено {} треков для userId={}", belpostTracks.size(), userId);
        }

        if (!others.isEmpty()) {
            List<TrackingResultAdd> results = trackUpdateService.process(others, userId);

            long updated = results.stream()
                    .filter(r -> !TrackConstants.NO_DATA_STATUS.equals(r.getStatus()))
                    .count();

            if (updated > 0) {
                log.info("♻️ Автообновление: {} из {} треков обновлено для userId={}", updated, others.size(), userId);
            }
        }
    }
}
