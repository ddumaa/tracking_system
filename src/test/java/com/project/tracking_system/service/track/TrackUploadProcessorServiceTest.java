package com.project.tracking_system.service.track;

import com.project.tracking_system.controller.WebSocketController;
import com.project.tracking_system.service.belpost.BelPostTrackQueueService;
import com.project.tracking_system.service.track.ProgressAggregatorService;
import com.project.tracking_system.service.track.TrackUpdateEligibilityService;
import com.project.tracking_system.service.track.TrackMetaValidator;
import com.project.tracking_system.service.track.TrackExcelParser;
import com.project.tracking_system.service.track.TrackExcelRow;
import com.project.tracking_system.service.track.TrackMeta;
import com.project.tracking_system.service.track.TrackMetaValidationResult;
import com.project.tracking_system.service.track.TrackUploadGroupingService;
import com.project.tracking_system.service.track.TrackUpdateDispatcherService;
import com.project.tracking_system.service.track.TrackingResultCacheService;
import com.project.tracking_system.entity.PostalServiceType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Тесты для {@link TrackUploadProcessorService}.
 * Проверяется корректная постановка треков в очередь и обработка
 * случаев отсутствия подходящих номеров.
 */
@ExtendWith(MockitoExtension.class)
class TrackUploadProcessorServiceTest {

    @Mock
    private TrackExcelParser parser;
    @Mock
    private BelPostTrackQueueService queueService;
    @Mock
    private TrackMetaValidator trackMetaValidator;
    @Mock
    private WebSocketController webSocketController;
    @Mock
    private ProgressAggregatorService progressAggregatorService;
    @Mock
    private TrackUpdateEligibilityService trackUpdateEligibilityService;
    @Mock
    private TrackUploadGroupingService groupingService;
    @Mock
    private TrackUpdateDispatcherService dispatcherService;
    @Mock
    private TrackingResultCacheService trackingResultCacheService;

    private TrackUploadProcessorService processor;

    @BeforeEach
    void setUp() {
        processor = new TrackUploadProcessorService(
                parser,
                queueService,
                webSocketController,
                trackMetaValidator,
                progressAggregatorService,
                trackUpdateEligibilityService,
                groupingService,
                dispatcherService,
                trackingResultCacheService
        );
    }

    /**
     * Проверяет, что валидные треки ставятся в очередь и обрабатываются.
     */
    @Test
    void process_EnqueuesTracks() throws Exception {
        MockMultipartFile file = new MockMultipartFile("f", new byte[0]);
        TrackMeta meta = new TrackMeta("A1", 1L, "p", true, PostalServiceType.BELPOST);
        when(parser.parse(file)).thenReturn(List.of(new TrackExcelRow("A1", "1", "p")));
        when(trackMetaValidator.validate(anyList(), eq(1L)))
                .thenReturn(new TrackMetaValidationResult(List.of(meta), List.of(), null));
        when(trackUpdateEligibilityService.canUpdate(anyString(), any())).thenReturn(true);
        when(groupingService.group(List.of(meta)))
                .thenReturn(java.util.Map.of(PostalServiceType.BELPOST, List.of(meta)));
        when(dispatcherService.dispatch(anyMap(), eq(1L)))
                .thenReturn(List.of(new com.project.tracking_system.dto.TrackingResultAdd("A1", "ok")));
        when(progressAggregatorService.getProgress(anyLong()))
                .thenReturn(new com.project.tracking_system.dto.TrackProcessingProgressDTO(1L, 1, 1, "0:00"));
        when(queueService.estimateWaitTime(1L)).thenReturn(java.time.Duration.ofSeconds(4));

        processor.process(file, 1L);

        verify(queueService).enqueue(anyList());
        verify(dispatcherService).dispatch(anyMap(), eq(1L));
        verify(trackingResultCacheService).addResult(eq(1L), any());
        verify(webSocketController, times(2)).sendUpdateStatus(eq(1L), contains("Белпочты"), eq(true));
        verify(webSocketController).sendTrackProcessingStarted(eq(1L), any());
        verify(progressAggregatorService).registerBatch(anyLong(), eq(1), eq(1L));
        verify(progressAggregatorService).trackProcessed(anyLong());
    }

    /**
     * Если подходящих для обновления треков нет, метод завершается без действий.
     */
    @Test
    void process_NoEligibleTracks_ReturnsEarly() throws Exception {
        MockMultipartFile file = new MockMultipartFile("f", new byte[0]);
        when(parser.parse(file)).thenReturn(List.of(new TrackExcelRow("A1", "1", "p")));
        when(trackMetaValidator.validate(anyList(), eq(1L)))
                .thenReturn(new TrackMetaValidationResult(
                        List.of(new TrackMeta("A1", 1L, "p", true)), List.of(), null));
        when(trackUpdateEligibilityService.canUpdate(anyString(), any())).thenReturn(false);

        processor.process(file, 1L);

        verify(progressAggregatorService, never()).registerBatch(anyLong(), anyInt(), any());
        verify(queueService, never()).enqueue(anyList());
        verify(webSocketController, never()).sendTrackProcessingStarted(anyLong(), any());
        verify(webSocketController).sendUpdateStatus(eq(1L), contains("нет"), eq(false));
    }
}
