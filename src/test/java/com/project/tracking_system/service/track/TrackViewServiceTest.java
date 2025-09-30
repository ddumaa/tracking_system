package com.project.tracking_system.service.track;

import com.project.tracking_system.dto.TrackDetailsDto;
import com.project.tracking_system.dto.TrackStatusEventDto;
import com.project.tracking_system.entity.DeliveryHistory;
import com.project.tracking_system.entity.GlobalStatus;
import com.project.tracking_system.entity.PostalServiceType;
import com.project.tracking_system.entity.Store;
import com.project.tracking_system.entity.TrackParcel;
import com.project.tracking_system.entity.TrackStatusEvent;
import com.project.tracking_system.service.admin.ApplicationSettingsService;
import com.project.tracking_system.service.user.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Тесты для {@link TrackViewService}.
 */
@ExtendWith(MockitoExtension.class)
class TrackViewServiceTest {

    @Mock
    private TrackParcelService trackParcelService;
    @Mock
    private UserService userService;
    @Mock
    private ApplicationSettingsService applicationSettingsService;
    @Mock
    private TrackStatusEventService trackStatusEventService;

    private TrackViewService service;

    @BeforeEach
    void setUp() {
        service = new TrackViewService(trackParcelService, trackStatusEventService,
                userService, applicationSettingsService);
    }

    /**
     * Убеждаемся, что при наличии сохранённых событий возвращается
     * история из {@link TrackStatusEventService} без обращения к фолбеку.
     */
    @Test
    void getTrackDetails_UsesPersistedEventsWhenAvailable() {
        ZonedDateTime now = ZonedDateTime.now(ZoneOffset.UTC);
        TrackParcel parcel = buildParcel(10L, GlobalStatus.IN_TRANSIT, now.minusHours(5));
        when(trackParcelService.findOwnedById(10L, 1L)).thenReturn(Optional.of(parcel));
        when(applicationSettingsService.getTrackUpdateIntervalHours()).thenReturn(3);
        when(userService.getUserZone(1L)).thenReturn(ZoneId.of("UTC"));

        TrackStatusEvent first = buildEvent(parcel, now.minusHours(1), "Принят в отделении");
        TrackStatusEvent second = buildEvent(parcel, now.minusHours(3), "Передан перевозчику");
        when(trackStatusEventService.findEvents(10L)).thenReturn(List.of(first, second));

        TrackDetailsDto details = service.getTrackDetails(10L, 1L);

        verify(trackStatusEventService).findEvents(10L);
        assertThat(details.history())
                .extracting(TrackStatusEventDto::status)
                .containsExactly("Принят в отделении", "Передан перевозчику");
    }

    /**
     * Проверяем, что при отсутствии сохранённых событий возвращается только агрегированный
     * статус с корректной меткой времени, без добавления жёстко заданных этапов.
     */
    @Test
    void getTrackDetails_BuildsFallbackFromAggregateStatusOnly() {
        ZonedDateTime statusMoment = ZonedDateTime.now(ZoneOffset.UTC).minusHours(2);
        TrackParcel parcel = buildParcel(20L, GlobalStatus.IN_TRANSIT, statusMoment);
        DeliveryHistory deliveryHistory = new DeliveryHistory();
        deliveryHistory.setPostalService(PostalServiceType.BELPOST);
        parcel.setDeliveryHistory(deliveryHistory);

        when(trackParcelService.findOwnedById(20L, 2L)).thenReturn(Optional.of(parcel));
        when(applicationSettingsService.getTrackUpdateIntervalHours()).thenReturn(6);
        when(userService.getUserZone(2L)).thenReturn(ZoneId.of("UTC"));
        when(trackStatusEventService.findEvents(20L)).thenReturn(List.of());

        TrackDetailsDto details = service.getTrackDetails(20L, 2L);

        verify(trackStatusEventService).findEvents(20L);
        assertThat(details.history()).hasSize(1);
        TrackStatusEventDto fallbackEvent = details.history().get(0);
        assertThat(fallbackEvent.status()).isEqualTo(GlobalStatus.IN_TRANSIT.getDescription());
        assertThat(fallbackEvent.timestamp()).isEqualTo(statusMoment.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
        assertThat(details.currentStatus()).isEqualTo(fallbackEvent);
    }

    /**
     * Если точная дата статуса не сохранена, используется отметка последнего обновления.
     */
    @Test
    void getTrackDetails_UsesLastUpdateWhenTimestampMissing() {
        ZonedDateTime lastUpdate = ZonedDateTime.now(ZoneOffset.UTC).minusHours(4);
        TrackParcel parcel = buildParcel(25L, GlobalStatus.WAITING_FOR_CUSTOMER, lastUpdate);
        parcel.setTimestamp(null);

        when(trackParcelService.findOwnedById(25L, 5L)).thenReturn(Optional.of(parcel));
        when(applicationSettingsService.getTrackUpdateIntervalHours()).thenReturn(12);
        when(userService.getUserZone(5L)).thenReturn(ZoneId.of("UTC"));
        when(trackStatusEventService.findEvents(25L)).thenReturn(List.of());

        TrackDetailsDto details = service.getTrackDetails(25L, 5L);

        assertThat(details.history()).hasSize(1);
        TrackStatusEventDto event = details.history().get(0);
        assertThat(event.status()).isEqualTo(GlobalStatus.WAITING_FOR_CUSTOMER.getDescription());
        assertThat(event.timestamp()).isEqualTo(lastUpdate.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
    }

    /**
     * Финальный статус запрещает ручное обновление и обнуляет время следующей попытки.
     */
    @Test
    void getTrackDetails_FinalStatusDisablesRefresh() {
        ZonedDateTime lastUpdate = ZonedDateTime.now(ZoneOffset.UTC).minusHours(1);
        TrackParcel parcel = buildParcel(30L, GlobalStatus.DELIVERED, lastUpdate);
        when(trackParcelService.findOwnedById(30L, 3L)).thenReturn(Optional.of(parcel));
        when(applicationSettingsService.getTrackUpdateIntervalHours()).thenReturn(4);
        when(userService.getUserZone(3L)).thenReturn(ZoneId.of("UTC"));
        when(trackStatusEventService.findEvents(30L)).thenReturn(List.of());

        TrackDetailsDto details = service.getTrackDetails(30L, 3L);

        assertThat(details.refreshAllowed()).isFalse();
        assertThat(details.nextRefreshAt()).isNull();
    }

    /**
     * Проверяем, что редактирование разрешено только для статусов PRE_REGISTERED и ERROR.
     */
    @Test
    void getTrackDetails_DisablesEditForTransitStatus() {
        TrackParcel parcel = buildParcel(42L, GlobalStatus.IN_TRANSIT, ZonedDateTime.now(ZoneOffset.UTC));
        when(trackParcelService.findOwnedById(42L, 8L)).thenReturn(Optional.of(parcel));
        when(applicationSettingsService.getTrackUpdateIntervalHours()).thenReturn(4);
        when(userService.getUserZone(8L)).thenReturn(ZoneId.of("UTC"));
        when(trackStatusEventService.findEvents(42L)).thenReturn(List.of());

        TrackDetailsDto details = service.getTrackDetails(42L, 8L);

        assertThat(details.canEditTrack()).isFalse();
    }

    /**
     * Создаёт тестовую посылку.
     */
    private static TrackParcel buildParcel(Long id, GlobalStatus status, ZonedDateTime update) {
        TrackParcel parcel = new TrackParcel();
        parcel.setId(id);
        parcel.setNumber("A1");
        parcel.setStatus(status);
        parcel.setLastUpdate(update);
        parcel.setTimestamp(update);
        Store store = new Store();
        store.setId(1L);
        parcel.setStore(store);
        return parcel;
    }

    /**
     * Формирует тестовое событие истории статусов.
     */
    private static TrackStatusEvent buildEvent(TrackParcel parcel, ZonedDateTime time, String description) {
        TrackStatusEvent event = new TrackStatusEvent();
        event.setTrackParcel(parcel);
        event.setEventTime(time);
        event.setDescription(description);
        return event;
    }
}
