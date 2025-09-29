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
                .extracting(TrackStatusEventDto::description)
                .containsExactly("Принят в отделении", "Передан перевозчику");
    }

    /**
     * Проверяем, что при отсутствии сохранённых событий строится резервная
     * история из данных посылки и истории доставки.
     */
    @Test
    void getTrackDetails_BuildsFallbackHistoryWhenEventsMissing() {
        ZonedDateTime now = ZonedDateTime.now(ZoneOffset.UTC);
        TrackParcel parcel = buildParcel(20L, GlobalStatus.IN_TRANSIT, now.minusHours(2));
        DeliveryHistory deliveryHistory = new DeliveryHistory();
        deliveryHistory.setPostalService(PostalServiceType.BELPOST);
        deliveryHistory.setSendDate(now.minusDays(3));
        deliveryHistory.setArrivedDate(now.minusDays(1));
        deliveryHistory.setReceivedDate(now.minusHours(12));
        parcel.setDeliveryHistory(deliveryHistory);

        when(trackParcelService.findOwnedById(20L, 2L)).thenReturn(Optional.of(parcel));
        when(applicationSettingsService.getTrackUpdateIntervalHours()).thenReturn(6);
        when(userService.getUserZone(2L)).thenReturn(ZoneId.of("UTC"));
        when(trackStatusEventService.findEvents(20L)).thenReturn(List.of());

        TrackDetailsDto details = service.getTrackDetails(20L, 2L);

        verify(trackStatusEventService).findEvents(20L);
        assertThat(details.history())
                .extracting(TrackStatusEventDto::description)
                .contains("Посылка зарегистрирована", "Прибытие на пункт выдачи", "Вручение получателю");
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
