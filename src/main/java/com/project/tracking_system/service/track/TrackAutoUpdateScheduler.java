package com.project.tracking_system.service.track;

import com.project.tracking_system.entity.TrackParcel;
import com.project.tracking_system.model.subscription.FeatureKey;
import com.project.tracking_system.repository.TrackParcelRepository;
import com.project.tracking_system.repository.UserSubscriptionRepository;
import com.project.tracking_system.service.SubscriptionService;
import com.project.tracking_system.service.user.UserService;
import com.project.tracking_system.dto.TrackingResultAdd;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    private final TrackUpdateCoordinatorService trackUpdateCoordinatorService;
    private final SubscriptionService subscriptionService;
    private final UserService userService;

    /**
     * Запускает автообновление треков для всех подходящих пользователей.
     */
    @Transactional
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
     * @param userId идентификатор пользователя
     */
    private void updateUserTracks(Long userId) {
        List<TrackParcel> parcels = trackParcelRepository.findByUserId(userId);
        List<TrackParcel> toUpdate = parcels.stream()
                .filter(p -> !p.getStatus().isFinal())
                .toList();

        if (toUpdate.isEmpty()) {
            return;
        }

        int allowed = subscriptionService.canUpdateTracks(userId, toUpdate.size());
        if (allowed <= 0) {
            log.debug("Лимит автообновлений исчерпан для userId={}", userId);
            return;
        }

        List<TrackMeta> metas = toUpdate.stream()
                .limit(Math.min(allowed, toUpdate.size()))
                .map(p -> new TrackMeta(p.getNumber(), p.getStore().getId(), null, true))
                .toList();

        List<TrackingResultAdd> results = trackUpdateCoordinatorService.process(metas, userId);

        long updated = results.stream()
                .filter(r -> !TrackConstants.NO_DATA_STATUS.equals(r.getStatus()))
                .count();

        if (updated > 0) {
            log.info("♻️ Автообновление: {} из {} треков обновлено для userId={}", updated, toUpdate.size(), userId);
        }
    }
}
