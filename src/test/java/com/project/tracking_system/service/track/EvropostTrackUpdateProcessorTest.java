package com.project.tracking_system.service.track;

import com.project.tracking_system.dto.TrackInfoListDTO;
import com.project.tracking_system.dto.TrackingResultAdd;
import com.project.tracking_system.service.track.TrackFacade;
import com.project.tracking_system.service.track.TrackMeta;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.task.SyncTaskExecutor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link EvropostTrackUpdateProcessor} single track processing.
 */
@ExtendWith(MockitoExtension.class)
class EvropostTrackUpdateProcessorTest {

    @Mock
    private TrackFacade trackFacade;

    private EvropostTrackUpdateProcessor processor;

    @BeforeEach
    void setUp() {
        processor = new EvropostTrackUpdateProcessor(trackFacade, new SyncTaskExecutor());
    }

    @Test
    void processSingle_ReturnsInfo() {
        TrackMeta meta = new TrackMeta("E1", 1L, null, false);
        TrackInfoListDTO info = new TrackInfoListDTO();
        when(trackFacade.processTrack("E1", 1L, null, false, null)).thenReturn(info);

        TrackingResultAdd result = processor.process(meta);

        verify(trackFacade).processTrack("E1", 1L, null, false, null);
        assertEquals(info, result.getTrackInfo());
    }
}
