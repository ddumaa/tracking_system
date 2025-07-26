package com.project.tracking_system.service.track;

import com.project.tracking_system.service.admin.ApplicationSettingsService;
import com.project.tracking_system.service.track.InvalidTrackReason;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests for {@link InvalidTrackCacheService}.
 */
@ExtendWith(MockitoExtension.class)
class InvalidTrackCacheServiceTest {

    @Mock
    private ApplicationSettingsService applicationSettingsService;

    private InvalidTrackCacheService service;

    @BeforeEach
    void setUp() {
        service = new InvalidTrackCacheService(applicationSettingsService);
    }

    @Test
    void removeExpired_RespectsUpdatedSetting() {
        when(applicationSettingsService.getResultCacheExpirationMs()).thenReturn(100L);

        service.addInvalidTracks(1L, 1L, List.of(new InvalidTrack("A", InvalidTrackReason.WRONG_FORMAT)));
        service.removeExpired();
        assertFalse(service.getInvalidTracks(1L, 1L).isEmpty());

        when(applicationSettingsService.getResultCacheExpirationMs()).thenReturn(0L);
        service.removeExpired();
        assertTrue(service.getInvalidTracks(1L, 1L).isEmpty());

        verify(applicationSettingsService, times(2)).getResultCacheExpirationMs();
    }

    @Test
    void getLatestInvalidTracks_ReturnsNewestBatch() {
        service.addInvalidTracks(1L, 1L, List.of(new InvalidTrack("A", InvalidTrackReason.EMPTY_NUMBER)));
        service.addInvalidTracks(1L, 2L, List.of(new InvalidTrack("B", InvalidTrackReason.DUPLICATE)));

        List<InvalidTrack> list = service.getLatestInvalidTracks(1L);

        assertEquals(1, list.size());
        assertEquals("B", list.get(0).number());
    }
}

