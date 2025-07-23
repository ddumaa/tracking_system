package com.project.tracking_system.service.track;

import com.project.tracking_system.service.belpost.BelPostTrackQueueService;
import com.project.tracking_system.service.belpost.QueuedTrack;
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

    /**
     * Добавляет трек в очередь, если разрешено его обновлять.
     *
     * @param number номер трека
     * @param storeId идентификатор магазина
     * @param userId идентификатор пользователя
     * @return {@code true}, если трек был поставлен в очередь
     */
    public boolean enqueueIfAllowed(String number, Long storeId, Long userId) {
        if (trackUpdateEligibilityService.canUpdate(number, userId)) {
            belPostTrackQueueService.enqueue(new QueuedTrack(
                    number,
                    userId,
                    storeId,
                    "MANUAL",
                    System.currentTimeMillis()
            ));
            return true;
        }
        return false;
    }
}
