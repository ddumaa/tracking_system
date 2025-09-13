package com.project.tracking_system.service.track;

import com.project.tracking_system.dto.TrackInfoListDTO;
import com.project.tracking_system.dto.TrackingResultAdd;
import com.project.tracking_system.entity.GlobalStatus;
import com.project.tracking_system.entity.PostalServiceType;
import com.project.tracking_system.entity.Store;
import com.project.tracking_system.entity.TrackParcel;
import com.project.tracking_system.service.track.TrackMeta;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Тесты для {@link TrackViewService}.
 */
@ExtendWith(MockitoExtension.class)
class TrackViewServiceTest {

    @Mock
    private TrackParcelService trackParcelService;
    @Mock
    private TrackUpdateDispatcherService trackUpdateDispatcherService;
    @Mock
    private TrackProcessingService trackProcessingService;
    @Mock
    private UserService userService;
    @Mock
    private ApplicationSettingsService applicationSettingsService;

    private TrackViewService service;

    @BeforeEach
    void setUp() {
        service = new TrackViewService(trackParcelService, trackUpdateDispatcherService,
                trackProcessingService, userService, applicationSettingsService);
    }

    /**
     * Посылка с финальным статусом не должна обновляться.
     */
    @Test
    void getTrackDetails_FinalStatus_DoesNotTriggerUpdate() {
        ZonedDateTime now = ZonedDateTime.now(ZoneOffset.UTC);
        TrackParcel parcel = buildParcel(GlobalStatus.DELIVERED, now.minusHours(5));
        when(trackParcelService.userOwnsParcel("A1", 1L)).thenReturn(true);
        when(trackParcelService.findByNumberAndUserId("A1", 1L)).thenReturn(parcel);
        when(applicationSettingsService.getTrackUpdateIntervalHours()).thenReturn(3);
        when(userService.getUserZone(1L)).thenReturn(ZoneId.systemDefault());

        service.getTrackDetails("A1", 1L);

        verify(trackUpdateDispatcherService, never()).dispatch(any(TrackMeta.class), any());
        verify(trackProcessingService, never()).save(anyString(), any(), anyLong(), anyLong());
    }

    /**
     * Обновление выполняется только после истечения интервала.
     */
    @Test
    void getTrackDetails_IntervalElapsed_TriggersUpdate() {
        ZonedDateTime lastUpdate = ZonedDateTime.now(ZoneOffset.UTC).minusHours(5);
        TrackParcel parcel = buildParcel(GlobalStatus.IN_TRANSIT, lastUpdate);
        when(trackParcelService.userOwnsParcel("A1", 1L)).thenReturn(true);
        when(trackParcelService.findByNumberAndUserId("A1", 1L)).thenReturn(parcel);
        when(applicationSettingsService.getTrackUpdateIntervalHours()).thenReturn(3);
        when(trackParcelService.getPostalServiceType("A1")).thenReturn(PostalServiceType.BELPOST);
        TrackInfoListDTO info = new TrackInfoListDTO();
        when(trackUpdateDispatcherService.dispatch(any(TrackMeta.class), eq(1L)))
                .thenReturn(new TrackingResultAdd("A1", "ok", info));

        service.getTrackDetails("A1", 1L);

        verify(trackUpdateDispatcherService).dispatch(any(TrackMeta.class), eq(1L));
        verify(trackProcessingService).save("A1", info, 1L, 1L);
    }

    /**
     * Если интервал ещё не прошёл, обновление не инициируется.
     */
    @Test
    void getTrackDetails_BeforeInterval_NoUpdate() {
        ZonedDateTime lastUpdate = ZonedDateTime.now(ZoneOffset.UTC).minusHours(1);
        TrackParcel parcel = buildParcel(GlobalStatus.IN_TRANSIT, lastUpdate);
        when(trackParcelService.userOwnsParcel("A1", 1L)).thenReturn(true);
        when(trackParcelService.findByNumberAndUserId("A1", 1L)).thenReturn(parcel);
        when(applicationSettingsService.getTrackUpdateIntervalHours()).thenReturn(3);
        when(userService.getUserZone(1L)).thenReturn(ZoneId.systemDefault());

        service.getTrackDetails("A1", 1L);

        verify(trackUpdateDispatcherService, never()).dispatch(any(TrackMeta.class), any());
        verify(trackProcessingService, never()).save(anyString(), any(), anyLong(), anyLong());
    }

    /**
     * Создаёт тестовую посылку.
     */
    private static TrackParcel buildParcel(GlobalStatus status, ZonedDateTime update) {
        TrackParcel parcel = new TrackParcel();
        parcel.setNumber("A1");
        parcel.setStatus(status);
        parcel.setLastUpdate(update);
        parcel.setTimestamp(update);
        Store store = new Store();
        store.setId(1L);
        parcel.setStore(store);
        return parcel;
    }
}
