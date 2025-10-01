package com.project.tracking_system.service.track;

import com.project.tracking_system.dto.TrackDetailsDto;
import com.project.tracking_system.dto.TrackStatusEventDto;
import com.project.tracking_system.entity.DeliveryHistory;
import com.project.tracking_system.entity.GlobalStatus;
import com.project.tracking_system.entity.PostalServiceType;
import com.project.tracking_system.entity.TrackParcel;
import com.project.tracking_system.entity.TrackStatusEvent;
import com.project.tracking_system.service.admin.ApplicationSettingsService;
import com.project.tracking_system.service.user.UserService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

/**
 * Сервис чтения сохранённых данных о посылке для модального окна.
 * <p>
 * Реализация не выполняет обращений к внешним почтовым сервисам и работает
 * только с данными, уже сохранёнными в БД, что снижает нагрузку и ускоряет
 * открытие модалки при повторных запросах.
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TrackViewService {

    private static final DateTimeFormatter ISO_FORMATTER = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    private final TrackParcelService trackParcelService;
    private final TrackStatusEventService trackStatusEventService;
    private final UserService userService;
    private final ApplicationSettingsService applicationSettingsService;

    /**
     * Возвращает DTO с данными о посылке для текущего пользователя.
     * <p>
     * Результат кэшируется на несколько секунд, чтобы повторное открытие модалки
     * не инициировало дополнительные запросы в БД (SRP: сервис считает только
     * бизнес-логику, кэш управляется инфраструктурой).
     * </p>
     *
     * @param trackId идентификатор посылки
     * @param userId  идентификатор пользователя
     * @return подготовленный DTO
     */
    @Transactional(readOnly = true)
    @Cacheable(cacheNames = "track-details", key = "#userId + ':' + #trackId")
    public TrackDetailsDto getTrackDetails(Long trackId, Long userId) {
        TrackParcel parcel = loadParcel(trackId, userId);
        ZoneId userZone = userService.getUserZone(userId);

        List<TrackStatusEventDto> history = buildHistory(parcel, userZone);
        TrackStatusEventDto currentStatus = history.isEmpty() ? null : history.get(0);

        boolean refreshAllowed = isRefreshAllowed(parcel);
        String nextRefreshAt = resolveNextRefreshAt(parcel, refreshAllowed, userZone);
        boolean canEditTrack = canEditTrack(parcel);

        PostalServiceType serviceType = Optional.ofNullable(parcel.getDeliveryHistory())
                .map(DeliveryHistory::getPostalService)
                .orElse(null);

        String systemStatus = Optional.ofNullable(parcel.getStatus())
                .map(GlobalStatus::getLabel)
                .orElse(null);
        String lastUpdateAt = Optional.ofNullable(parcel.getLastUpdate())
                .map(moment -> formatTimestamp(moment, userZone))
                .orElse(null);

        return new TrackDetailsDto(
                parcel.getId(),
                parcel.getNumber(),
                serviceType != null ? serviceType.getDisplayName() : null,
                systemStatus,
                lastUpdateAt,
                currentStatus,
                history,
                refreshAllowed,
                nextRefreshAt,
                canEditTrack,
                userZone.getId()
        );
    }

    /**
     * Загружает посылку и проверяет права доступа.
     */
    private TrackParcel loadParcel(Long trackId, Long userId) {
        Optional<TrackParcel> owned = trackParcelService.findOwnedById(trackId, userId);
        if (owned.isPresent()) {
            return owned.get();
        }
        boolean exists = trackParcelService.findById(trackId).isPresent();
        if (exists) {
            throw new AccessDeniedException("Посылка не принадлежит пользователю");
        }
        log.warn("Не найдена посылка id={} для пользователя {}", trackId, userId);
        throw new EntityNotFoundException("Посылка не найдена");
    }

    /**
     * Возвращает сохранённую историю статусов с учётом пользовательской зоны.
     */
    private List<TrackStatusEventDto> buildHistory(TrackParcel parcel, ZoneId userZone) {
        List<TrackStatusEvent> events = trackStatusEventService.findEvents(parcel.getId());
        if (!events.isEmpty()) {
            return events.stream()
                    .map(event -> new TrackStatusEventDto(
                            extractShortStatus(event.getDescription()),
                            formatTimestamp(event.getEventTime(), userZone),
                            event.getDescription()))
                    .toList();
        }
        return buildFallbackHistory(parcel, userZone);
    }

    /**
     * Формирует минимальную историю на основе агрегированного статуса, если
     * детальные события ещё не сохранены.
     */
    private List<TrackStatusEventDto> buildFallbackHistory(TrackParcel parcel, ZoneId userZone) {
        GlobalStatus aggregateStatus = parcel.getStatus();
        ZonedDateTime aggregateMoment = resolveStatusMoment(parcel);
        if (aggregateStatus == null || aggregateMoment == null) {
            return List.of();
        }
        String description = aggregateStatus.getDescription();
        return List.of(new TrackStatusEventDto(
                description,
                formatTimestamp(aggregateMoment, userZone)
        ));
    }

    /**
     * Определяет, можно ли инициировать обновление трека.
     */
    private boolean isRefreshAllowed(TrackParcel parcel) {
        if (parcel.getStatus() != null && parcel.getStatus().isFinal()) {
            return false;
        }
        int interval = applicationSettingsService.getTrackUpdateIntervalHours();
        ZonedDateTime threshold = ZonedDateTime.now(ZoneOffset.UTC).minusHours(interval);
        ZonedDateTime lastUpdate = parcel.getLastUpdate();
        return lastUpdate == null || lastUpdate.isBefore(threshold);
    }

    /**
     * Вычисляет момент следующего допустимого обновления.
     */
    private String resolveNextRefreshAt(TrackParcel parcel, boolean refreshAllowed, ZoneId userZone) {
        if (refreshAllowed || parcel.getStatus() != null && parcel.getStatus().isFinal()) {
            return null;
        }
        int interval = applicationSettingsService.getTrackUpdateIntervalHours();
        ZonedDateTime nextUpdate = parcel.getLastUpdate().plusHours(interval);
        return formatTimestamp(nextUpdate, userZone);
    }

    /**
     * Определяет, разрешено ли редактирование трека.
     */
    private boolean canEditTrack(TrackParcel parcel) {
        GlobalStatus status = parcel.getStatus();
        if (status == null) {
            return true;
        }
        return status == GlobalStatus.PRE_REGISTERED || status == GlobalStatus.ERROR;
    }

    /**
     * Приводит дату к ISO-формату с учётом пользовательского часового пояса.
     */
    private String formatTimestamp(ZonedDateTime moment, ZoneId userZone) {
        return moment.withZoneSameInstant(userZone).format(ISO_FORMATTER);
    }

    /**
     * Определяет момент времени для обобщённого статуса посылки.
     * <p>
     * Если точная дата статуса неизвестна (например, история ещё не загружена
     * из внешнего сервиса), используем отметку последнего обновления трека,
     * чтобы пользователь видел актуальную временную метку.
     * </p>
     *
     * @param parcel посылка, для которой нужно определить момент статуса
     * @return момент статуса или {@code null}, если его невозможно вычислить
     */
    private ZonedDateTime resolveStatusMoment(TrackParcel parcel) {
        ZonedDateTime statusMoment = parcel.getTimestamp();
        if (statusMoment != null) {
            return statusMoment;
        }
        return parcel.getLastUpdate();
    }

    /**
     * Формирует краткий заголовок для события таймлайна.
     * <p>
     * Если текст совпадает с описанием одного из глобальных статусов, возвращаем его.
     * В противном случае пытаемся отделить первую фразу до разделителей, чтобы показать
     * компактный заголовок, а полный текст отдаём в поле деталей.
     * </p>
     *
     * @param description исходное описание события
     * @return человеко-читаемый заголовок
     */
    private String extractShortStatus(String description) {
        if (description == null || description.isBlank()) {
            return "Обновление статуса";
        }
        GlobalStatus matched = GlobalStatus.fromDescription(description);
        if (matched != GlobalStatus.UNKNOWN_STATUS) {
            return matched.getLabel();
        }
        int separatorIndex = findSeparatorIndex(description);
        if (separatorIndex > 0) {
            return description.substring(0, separatorIndex).trim();
        }
        if (description.length() > 64) {
            return description.substring(0, 61).trim() + "…";
        }
        return description.trim();
    }

    /**
     * Ищет позицию первого разделителя в строке описания.
     *
     * @param description строка описания события
     * @return индекс разделителя или {@code -1}, если не найден
     */
    private int findSeparatorIndex(String description) {
        int colon = description.indexOf(':');
        int dash = description.indexOf('—');
        int hyphen = description.indexOf('-');
        int dot = description.indexOf('.');
        int separator = -1;
        for (int index : new int[]{colon, dash, hyphen, dot}) {
            if (index > 0 && (separator == -1 || index < separator)) {
                separator = index;
            }
        }
        return separator;
    }
}

