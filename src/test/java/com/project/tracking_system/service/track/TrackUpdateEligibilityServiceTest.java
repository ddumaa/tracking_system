package com.project.tracking_system.service.track;

import com.project.tracking_system.entity.GlobalStatus;
import com.project.tracking_system.entity.TrackParcel;
import com.project.tracking_system.service.admin.ApplicationSettingsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Тесты для {@link TrackUpdateEligibilityService}.
 */
@ExtendWith(MockitoExtension.class)
class TrackUpdateEligibilityServiceTest {

    @Mock
    private TrackParcelService trackParcelService;
    @Mock
    private ApplicationSettingsService applicationSettingsService;

    private TrackUpdateEligibilityService service;

    @BeforeEach
    void setUp() {
        service = new TrackUpdateEligibilityService(trackParcelService, applicationSettingsService);
    }

    /**
     * Новый трек должен обновляться без ограничений.
     */
    @Test
    void canUpdate_NewTrack_ReturnsTrue() {
        when(trackParcelService.findByNumberAndUserId("N1", 1L)).thenReturn(null);
        when(applicationSettingsService.getTrackUpdateIntervalHours()).thenReturn(3);

        assertTrue(service.canUpdate("N1", 1L));
    }

    /**
     * Если обновление было давно, метод должен вернуть {@code true}.
     */
    @Test
    void canUpdate_StaleTrack_ReturnsTrue() {
        TrackParcel parcel = buildParcel(GlobalStatus.IN_TRANSIT, ZonedDateTime.now(ZoneOffset.UTC).minusHours(5));
        when(trackParcelService.findByNumberAndUserId("S1", 1L)).thenReturn(parcel);
        when(applicationSettingsService.getTrackUpdateIntervalHours()).thenReturn(3);

        assertTrue(service.canUpdate("S1", 1L));
    }

    /**
     * Трек в финальном статусе не должен обновляться.
     */
    @Test
    void canUpdate_FinalStatus_ReturnsFalse() {
        TrackParcel parcel = buildParcel(GlobalStatus.DELIVERED, ZonedDateTime.now(ZoneOffset.UTC).minusHours(10));
        when(trackParcelService.findByNumberAndUserId("F1", 1L)).thenReturn(parcel);
        when(applicationSettingsService.getTrackUpdateIntervalHours()).thenReturn(3);

        assertFalse(service.canUpdate("F1", 1L));
    }

    /**
     * Если обновление выполнено недавно, метод возвращает {@code false}.
     */
    @Test
    void canUpdate_RecentUpdate_ReturnsFalse() {
        TrackParcel parcel = buildParcel(GlobalStatus.IN_TRANSIT, ZonedDateTime.now(ZoneOffset.UTC).minusHours(1));
        when(trackParcelService.findByNumberAndUserId("R1", 1L)).thenReturn(parcel);
        when(applicationSettingsService.getTrackUpdateIntervalHours()).thenReturn(3);

        assertFalse(service.canUpdate("R1", 1L));
    }

    /**
     * По умолчанию используется интервал в 3 часа.
     */
    @Test
    void canUpdate_UsesDefaultInterval() {
        TrackParcel parcel = buildParcel(GlobalStatus.IN_TRANSIT, ZonedDateTime.now(ZoneOffset.UTC).minusHours(2));
        when(trackParcelService.findByNumberAndUserId("D1", 1L)).thenReturn(parcel);
        when(applicationSettingsService.getTrackUpdateIntervalHours()).thenReturn(3);

        assertFalse(service.canUpdate("D1", 1L));
    }

    /**
     * Создаёт тестовую посылку с заданным статусом и временем обновления.
     */
    private TrackParcel buildParcel(GlobalStatus status, ZonedDateTime lastUpdate) {
        TrackParcel parcel = new TrackParcel();
        parcel.setStatus(status);
        parcel.setLastUpdate(lastUpdate);
        return parcel;
    }
}
