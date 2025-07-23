package com.project.tracking_system.service.track;

import com.project.tracking_system.entity.TrackParcel;
import com.project.tracking_system.service.admin.ApplicationSettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;

/**
 * Сервис проверки возможности обновления трека.
 * <p>
 * Решение основывается на времени последнего обновления и текущем статусе
 * посылки. Треки с финальным статусом никогда не ставятся в очередь.
 * </p>
 */
@Service
@RequiredArgsConstructor
public class TrackUpdateEligibilityService {

    private final TrackParcelService trackParcelService;
    private final ApplicationSettingsService applicationSettingsService;

    /**
     * Проверяет, можно ли обновлять трек.
     *
     * @param number номер трека
     * @param userId идентификатор владельца
     * @return {@code true}, если трек новый либо его обновление было
     *         раньше установленного интервала
     */
    public boolean canUpdate(String number, Long userId) {
        if (userId == null || number == null) {
            return false;
        }
        TrackParcel parcel = trackParcelService.findByNumberAndUserId(number.toUpperCase(), userId);
        if (parcel == null) {
            return true;
        }
        if (parcel.getStatus().isFinal()) {
            return false;
        }
        int interval = applicationSettingsService.getTrackUpdateIntervalHours();
        ZonedDateTime threshold = ZonedDateTime.now(ZoneOffset.UTC).minusHours(interval);
        return parcel.getLastUpdate() == null || parcel.getLastUpdate().isBefore(threshold);
    }
}
