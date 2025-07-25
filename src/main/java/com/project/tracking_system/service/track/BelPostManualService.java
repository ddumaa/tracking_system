package com.project.tracking_system.service.track;

import com.project.tracking_system.service.belpost.BelPostTrackQueueService;
import com.project.tracking_system.service.belpost.QueuedTrack;
import com.project.tracking_system.service.track.TrackSource;
import com.project.tracking_system.controller.WebSocketController;
import com.project.tracking_system.utils.DurationUtils;
import com.project.tracking_system.utils.TrackNumberUtils;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Сервис постановки в очередь треков Белпочты, добавленных вручную.
 */
@Service
@RequiredArgsConstructor
public class BelPostManualService {

    private final BelPostTrackQueueService belPostTrackQueueService;
    private final TrackUpdateEligibilityService trackUpdateEligibilityService;
    private final WebSocketController webSocketController;

    /**
     * Добавляет трек в очередь, если разрешено его обновлять.
     *
     * @param number номер трека
     * @param storeId идентификатор магазина
     * @param userId идентификатор пользователя
     * @param phone телефон покупателя (может быть {@code null})
     * @return {@code true}, если трек был поставлен в очередь
     */
    public boolean enqueueIfAllowed(String number, Long storeId, Long userId, String phone) {
        String normalized = TrackNumberUtils.normalize(number);
        if (trackUpdateEligibilityService.canUpdate(normalized, userId)) {
            belPostTrackQueueService.enqueue(new QueuedTrack(
                    normalized,
                    userId,
                    storeId,
                    TrackSource.MANUAL,
                    System.currentTimeMillis(),
                    phone
            ));

            webSocketController.sendUpdateStatus(
                    userId,
                    "Трек '" + normalized + "' поставлен в очередь Белпочты",
                    true
            );

            Duration wait = belPostTrackQueueService.estimateWaitTime(userId);
            if (wait != null && !wait.isZero()) {
                String eta = DurationUtils.formatMinutesSeconds(wait);
                webSocketController.sendUpdateStatus(userId,
                        "Треки Белпочты в очереди. Ожидание до начала обработки: " + eta,
                        true);
            }

            return true;
        }
        return false;
    }
}
