package com.project.tracking_system.service.track;

import com.project.tracking_system.dto.TrackInfoDTO;
import com.project.tracking_system.dto.TrackInfoListDTO;
import com.project.tracking_system.dto.TrackingResultAdd;
import com.project.tracking_system.entity.PostalServiceType;
import com.project.tracking_system.service.belpost.WebBelPostBatchService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TrackBatchProcessingServiceTest {

    @Mock
    private TrackFacade trackFacade;
    @Mock
    private WebBelPostBatchService webBelPostBatchService;

    private TrackBatchProcessingService service;

    @BeforeEach
    void setUp() {
        service = new TrackBatchProcessingService(trackFacade, webBelPostBatchService);
    }

    @Test
    void processBatch_EvroTracksProcessedConcurrently() throws Exception {
        CountDownLatch startLatch = new CountDownLatch(2);
        CountDownLatch finishLatch = new CountDownLatch(2);

        when(trackFacade.processTrack(anyString(), any(), any(), anyBoolean(), any()))
                .thenAnswer(invocation -> {
                    startLatch.countDown();
                    startLatch.await(1, TimeUnit.SECONDS);
                    TrackInfoListDTO dto = new TrackInfoListDTO();
                    dto.addTrackInfo(new TrackInfoDTO("time", "ok"));
                    finishLatch.countDown();
                    return dto;
                });

        Map<PostalServiceType, List<TrackMeta>> map = Map.of(
                PostalServiceType.EVROPOST,
                List.of(new TrackMeta("E1", 1L, null, true),
                        new TrackMeta("E2", 1L, null, true))
        );

        service.processBatch(map, 1L);

        assertTrue(finishLatch.await(1, TimeUnit.SECONDS),
                "Tracks should be processed concurrently");
    }
}
