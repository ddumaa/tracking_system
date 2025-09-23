package com.project.tracking_system.service.track;

import com.project.tracking_system.dto.TrackInfoDTO;
import com.project.tracking_system.dto.TrackInfoListDTO;
import com.project.tracking_system.dto.TrackingResultAdd;
import com.project.tracking_system.service.track.TrackMeta;
import com.project.tracking_system.service.track.TrackProcessingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.List;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link EvropostTrackUpdateProcessor} single track processing.
 */
@ExtendWith(MockitoExtension.class)
class EvropostTrackUpdateProcessorTest {

    @Mock
    private TrackProcessingService trackProcessingService;

    private EvropostTrackUpdateProcessor processor;

    @BeforeEach
    void setUp() {
        processor = new EvropostTrackUpdateProcessor(trackProcessingService, new SyncTaskExecutor());
    }

    @Test
    void processSingle_ReturnsInfo() {
        TrackMeta meta = new TrackMeta("E1", 1L, null, false);
        TrackInfoListDTO info = new TrackInfoListDTO();
        when(trackProcessingService.processTrack("E1", 1L, null, false, null)).thenReturn(info);

        TrackingResultAdd result = processor.process(meta);

        verify(trackProcessingService).processTrack("E1", 1L, null, false, null);
        assertEquals(info, result.getTrackInfo());
    }

    /**
     * Проверяет, что при объёме задач больше прежних лимитов обработка завершается без ошибок перегрузки.
     */
    @Test
    void processManyTracks_CompletesWithoutRejection() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(1);
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.initialize();

        try {
            EvropostTrackUpdateProcessor stressedProcessor = new EvropostTrackUpdateProcessor(trackProcessingService, executor);

            int trackCount = 120;
            List<TrackMeta> tracks = IntStream.range(0, trackCount)
                    .mapToObj(i -> new TrackMeta("EV" + i, 1L, null, false))
                    .toList();

            when(trackProcessingService.processTrack(anyString(), anyLong(), any(), anyBoolean(), any()))
                    .thenAnswer(invocation -> {
                        TrackInfoListDTO trackInfo = new TrackInfoListDTO();
                        trackInfo.getList().add(new TrackInfoDTO(null, "IN_TRANSIT"));
                        return trackInfo;
                    });

            List<TrackingResultAdd> results = stressedProcessor.process(tracks, 99L);

            assertEquals(trackCount, results.size());
            results.forEach(result -> assertEquals("IN_TRANSIT", result.getStatus()));
            verify(trackProcessingService, times(trackCount))
                    .processTrack(anyString(), anyLong(), eq(99L), anyBoolean(), any());
        } finally {
            executor.shutdown();
        }
    }
}
