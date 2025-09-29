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
import java.util.ArrayList;
import java.util.Comparator;
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

        return new TrackDetailsDto(
                parcel.getId(),
                parcel.getNumber(),
                serviceType != null ? serviceType.getDisplayName() : null,
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
                            event.getDescription(),
                            formatTimestamp(event.getEventTime(), userZone)))
                    .toList();
        }
        return buildFallbackHistory(parcel, userZone);
    }

    /**
     * Формирует историю, если подробные события ещё не сохранены.
     */
    private List<TrackStatusEventDto> buildFallbackHistory(TrackParcel parcel, ZoneId userZone) {
        List<StatusSnapshot> snapshots = new ArrayList<>();
        if (parcel.getTimestamp() != null && parcel.getStatus() != null) {
            snapshots.add(new StatusSnapshot(parcel.getTimestamp(), parcel.getStatus().getDescription()));
        }
        DeliveryHistory history = parcel.getDeliveryHistory();
        if (history != null) {
            appendIfPresent(snapshots, history.getSendDate(), "Посылка зарегистрирована");
            appendIfPresent(snapshots, history.getArrivedDate(), "Прибытие на пункт выдачи");
            appendIfPresent(snapshots, history.getReceivedDate(), "Вручение получателю");
            appendIfPresent(snapshots, history.getReturnedDate(), "Возврат отправителю");
        }

        snapshots.sort(Comparator.comparing(StatusSnapshot::moment).reversed());

        return snapshots.stream()
                .map(snapshot -> new TrackStatusEventDto(
                        snapshot.status(),
                        formatTimestamp(snapshot.moment(), userZone)))
                .toList();
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
        return status == GlobalStatus.PRE_REGISTERED || !status.isFinal();
    }

    /**
     * Добавляет событие в коллекцию, если дата не пуста.
     */
    private void appendIfPresent(List<StatusSnapshot> snapshots, ZonedDateTime moment, String status) {
        if (moment != null) {
            snapshots.add(new StatusSnapshot(moment, status));
        }
    }

    /**
     * Преобразует дату к ISO-формату в часовом поясе пользователя.
     */
    private String formatTimestamp(ZonedDateTime moment, ZoneId userZone) {
        return moment.withZoneSameInstant(userZone).format(ISO_FORMATTER);
    }

    /**
     * Небольшой внутренний record, чтобы не плодить пары List<Object>.
     */
    private record StatusSnapshot(ZonedDateTime moment, String status) {
    }
}

