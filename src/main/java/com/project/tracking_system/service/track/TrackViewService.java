package com.project.tracking_system.service.track;

import com.project.tracking_system.dto.TrackInfoDTO;
import com.project.tracking_system.dto.TrackInfoListDTO;
import com.project.tracking_system.dto.TrackViewResult;
import com.project.tracking_system.entity.TrackParcel;
import com.project.tracking_system.service.admin.ApplicationSettingsService;
import com.project.tracking_system.service.user.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Сервис получения детальной информации о посылке.
 * <p>
 * Проверяет, можно ли обновлять трек прямо сейчас,
 * при необходимости обновляет его и сохраняет изменения.
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TrackViewService {

    private final TrackParcelService trackParcelService;
    private final TrackUpdateDispatcherService trackUpdateDispatcherService;
    private final TrackProcessingService trackProcessingService;
    private final UserService userService;
    private final ApplicationSettingsService applicationSettingsService;

    /**
     * Возвращает информацию о посылке для отображения в интерфейсе.
     * <p>
     * Если последнее обновление было достаточно давно, трек будет
     * обновлён и сохранён. В противном случае вернётся текущее состояние
     * и время следующего допустимого обновления.
     * </p>
     *
     * @param itemNumber номер посылки
     * @param userId     идентификатор пользователя
     * @return объект с историей трека и возможным временем следующего обновления
     * @throws AccessDeniedException    если посылка не принадлежит пользователю
     * @throws EntityNotFoundException  если посылка не найдена
     */
    @Transactional
    public TrackViewResult getTrackDetails(String itemNumber, Long userId) {
        // Проверяем принадлежность посылки
        if (!trackParcelService.userOwnsParcel(itemNumber, userId)) {
            log.warn("❌ Пользователь ID={} попытался получить чужой трек {}", userId, itemNumber);
            throw new AccessDeniedException("Посылка не принадлежит пользователю");
        }

        TrackParcel parcel = trackParcelService.findByNumberAndUserId(itemNumber, userId);
        if (parcel == null) {
            throw new EntityNotFoundException("Посылка не найдена");
        }

        int interval = applicationSettingsService.getTrackUpdateIntervalHours();
        ZonedDateTime nowUtc = ZonedDateTime.now(ZoneOffset.UTC);
        ZonedDateTime nextAllowed = parcel.getLastUpdate().plusHours(interval);
        // Посылку можно обновлять, если статус ещё не финальный и с момента
        // последнего обновления прошло достаточно времени
        boolean canUpdate = !parcel.getStatus().isFinal()
                && (parcel.getLastUpdate() == null
                || parcel.getLastUpdate().isBefore(nowUtc.minusHours(interval)));

        TrackInfoListDTO trackInfo;
        String nextUpdateTime = null;
        if (canUpdate) {
            TrackMeta meta = new TrackMeta(itemNumber, null, null, false,
                    trackParcelService.getPostalServiceType(itemNumber));
            trackInfo = trackUpdateDispatcherService.dispatch(meta).getTrackInfo();
            trackProcessingService.save(itemNumber, trackInfo, parcel.getStore().getId(), userId);
            log.info("🎯 Передано {} записей для трека {}", trackInfo.getList().size(), itemNumber);
        } else {
            trackInfo = new TrackInfoListDTO();
            String ts = parcel.getTimestamp()
                    .withZoneSameInstant(userService.getUserZone(userId))
                    .format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss"));
            trackInfo.addTrackInfo(new TrackInfoDTO(ts, parcel.getStatus().getDescription()));
            nextUpdateTime = nextAllowed.withZoneSameInstant(userService.getUserZone(userId))
                    .format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss"));
        }

        return new TrackViewResult(trackInfo, nextUpdateTime);
    }
}
