package com.project.tracking_system.service.track;

import com.project.tracking_system.entity.TrackParcel;
import com.project.tracking_system.model.subscription.FeatureKey;
import com.project.tracking_system.repository.TrackParcelRepository;
import com.project.tracking_system.repository.UserSubscriptionRepository;
import com.project.tracking_system.service.SubscriptionService;
import com.project.tracking_system.dto.TrackInfoListDTO;
import com.project.tracking_system.service.track.TrackProcessingService;
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
    private final TrackProcessingService trackProcessingService;
    private final SubscriptionService subscriptionService;

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
            updateUserTracks(userId);
        }
    }

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

        int updated = 0;
        for (int i = 0; i < Math.min(allowed, toUpdate.size()); i++) {
            TrackParcel parcel = toUpdate.get(i);
            try {
                TrackInfoListDTO info = trackProcessingService.processTrack(
                        parcel.getNumber(),
                        parcel.getStore().getId(),
                        userId,
                        true
                );
                if (info != null && !info.getList().isEmpty()) {
                    updated++;
                }
            } catch (Exception e) {
                log.error("Ошибка автообновления трека {}: {}", parcel.getNumber(), e.getMessage());
            }
        }

        if (updated > 0) {
            log.info("♻️ Автообновление: {} из {} треков обновлено для userId={}", updated, toUpdate.size(), userId);
        }
    }
}
