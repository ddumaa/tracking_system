package com.project.tracking_system.service.track;

import com.project.tracking_system.dto.TrackStatusUpdateDTO;
import com.project.tracking_system.service.admin.ApplicationSettingsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests for {@link TrackingResultCacheService}.
 */
@ExtendWith(MockitoExtension.class)
class TrackingResultCacheServiceTest {

    @Mock
    private ApplicationSettingsService applicationSettingsService;

    private TrackingResultCacheService service;

    @BeforeEach
    void setUp() {
        service = new TrackingResultCacheService(applicationSettingsService);
    }

    @Test
    void removeExpired_RespectsUpdatedSetting() {
        when(applicationSettingsService.getResultCacheExpirationMs()).thenReturn(100L);

        service.addResult(1L, new TrackStatusUpdateDTO(1L, "A1", "ok", 1, 1));
        service.removeExpired();
        assertFalse(service.getResults(1L, 1L).isEmpty());

        when(applicationSettingsService.getResultCacheExpirationMs()).thenReturn(0L);
        service.removeExpired();
        assertTrue(service.getResults(1L, 1L).isEmpty());

        verify(applicationSettingsService, times(2)).getResultCacheExpirationMs();
    }

    @Test
    void notViewed_EntriesIgnoredUntilFirstAccess() {
        when(applicationSettingsService.getResultCacheExpirationMs()).thenReturn(0L);

        service.addResult(1L, new TrackStatusUpdateDTO(1L, "A1", "ok", 1, 1));
        service.removeExpired();
        assertFalse(service.getResults(1L, 1L).isEmpty(), "Cache should persist until viewed");

        // first access should mark entry as viewed
        service.getResults(1L, 1L);
        service.removeExpired();
        assertTrue(service.getResults(1L, 1L).isEmpty(), "Cache should expire after viewing when TTL elapsed");
    }
}
