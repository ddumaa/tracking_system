package com.project.tracking_system.service.track;

import com.project.tracking_system.dto.TrackInfoListDTO;
import com.project.tracking_system.dto.TrackingResultAdd;
import com.project.tracking_system.service.belpost.WebBelPostBatchService;
import com.project.tracking_system.service.track.TrackMeta;
import com.project.tracking_system.service.track.TrackProcessingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link BelpostTrackUpdateProcessor} single track processing.
 */
@ExtendWith(MockitoExtension.class)
class BelpostTrackUpdateProcessorTest {

    @Mock
    private TrackProcessingService trackProcessingService;
    @Mock
    private WebBelPostBatchService webService;

    private BelpostTrackUpdateProcessor processor;

    @BeforeEach
    void setUp() {
        processor = new BelpostTrackUpdateProcessor(trackProcessingService, webService);
    }

    @Test
    void processSingle_SavesWhenAllowed() {
        TrackMeta meta = new TrackMeta("B1", 1L, null, true);
        TrackInfoListDTO info = new TrackInfoListDTO();
        when(webService.processBatch(List.of("B1"))).thenReturn(Map.of("B1", info));

        TrackingResultAdd result = processor.process(meta, 1L);

        verify(trackProcessingService).save("B1", info, 1L, 1L, null);
        assertEquals(info, result.getTrackInfo());
    }
}
