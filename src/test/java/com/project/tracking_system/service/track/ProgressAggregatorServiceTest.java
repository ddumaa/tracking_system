package com.project.tracking_system.service.track;

import com.project.tracking_system.controller.WebSocketController;
import com.project.tracking_system.dto.TrackProcessingProgressDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Тесты для {@link ProgressAggregatorService}.
 */
@ExtendWith(MockitoExtension.class)
class ProgressAggregatorServiceTest {

    @Mock
    private WebSocketController webSocketController;

    private ProgressAggregatorService service;

    @BeforeEach
    void setUp() {
        service = new ProgressAggregatorService(webSocketController);
    }

    @Test
    void registerBatch_SendsInitialProgress() {
        service.registerBatch(1L, 2, 5L);
        verify(webSocketController).sendProgress(eq(5L), any());
        TrackProcessingProgressDTO dto = service.getProgress(1L);
        assertEquals(0, dto.processed());
        assertEquals(2, dto.total());
    }

    @Test
    void trackProcessed_IncrementsAndCompletes() {
        service.registerBatch(2L, 1, 7L);
        service.trackProcessed(2L);
        verify(webSocketController, times(2)).sendProgress(eq(7L), any());
        TrackProcessingProgressDTO dto = service.getProgress(2L);
        assertEquals(0, dto.total());
    }

    @Test
    void getLatestBatchId_ReturnsMostRecent() throws InterruptedException {
        service.registerBatch(1L, 1, 5L);
        Thread.sleep(2);
        service.registerBatch(2L, 1, 5L);

        Long latest = service.getLatestBatchId(5L);

        assertEquals(2L, latest);
    }

    @Test
    void getLatestBatchId_ReturnsNullWhenNothing() {
        assertNull(service.getLatestBatchId(99L));
    }
}
