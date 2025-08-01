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
     * Новый трек отсутствует в базе и может быть обновлён.
     */
    @Test
    void canUpdate_NewTrack_ReturnsTrue() {
        when(trackParcelService.findByNumberAndUserId("A1", 1L)).thenReturn(null);

        boolean result = service.canUpdate("A1", 1L);

        assertTrue(result);
    }

    /**
     * Посылка с финальным статусом не подлежит обновлению.
     */
    @Test
    void canUpdate_FinalStatusParcel_ReturnsFalse() {
        TrackParcel parcel = new TrackParcel();
        parcel.setStatus(GlobalStatus.DELIVERED);
        when(trackParcelService.findByNumberAndUserId("A1", 1L)).thenReturn(parcel);

        boolean result = service.canUpdate("A1", 1L);

        assertFalse(result);
    }

    /**
     * Недавно обновлённая посылка игнорируется.
     */
    @Test
    void canUpdate_RecentlyUpdated_ReturnsFalse() {
        TrackParcel parcel = new TrackParcel();
        parcel.setStatus(GlobalStatus.IN_TRANSIT);
        parcel.setLastUpdate(ZonedDateTime.now(ZoneOffset.UTC).minusHours(1));
        when(trackParcelService.findByNumberAndUserId("A1", 1L)).thenReturn(parcel);
        when(applicationSettingsService.getTrackUpdateIntervalHours()).thenReturn(3);

        boolean result = service.canUpdate("A1", 1L);

        assertFalse(result);
    }

    /**
     * Если с момента обновления прошло больше установленного интервала, трек можно обновить.
     */
    @Test
    void canUpdate_StaleParcel_ReturnsTrue() {
        TrackParcel parcel = new TrackParcel();
        parcel.setStatus(GlobalStatus.IN_TRANSIT);
        parcel.setLastUpdate(ZonedDateTime.now(ZoneOffset.UTC).minusHours(5));
        when(trackParcelService.findByNumberAndUserId("A1", 1L)).thenReturn(parcel);
        when(applicationSettingsService.getTrackUpdateIntervalHours()).thenReturn(3);

        boolean result = service.canUpdate("A1", 1L);

        assertTrue(result);
    }
}
