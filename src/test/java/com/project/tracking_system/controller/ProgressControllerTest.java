package com.project.tracking_system.controller;

import com.project.tracking_system.dto.TrackProcessingProgressDTO;
import com.project.tracking_system.service.belpost.BelPostTrackQueueService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

/**
 * Тесты для {@link ProgressController}.
 */
@ExtendWith(MockitoExtension.class)
class ProgressControllerTest {

    @Mock
    private BelPostTrackQueueService queueService;
    @Mock
    private BelPostTrackQueueService.BatchProgress progress;

    @InjectMocks
    private ProgressController controller;

    @Test
    void getProgress_ReturnsDto() {
        when(queueService.getProgress(5L)).thenReturn(progress);
        when(progress.getProcessed()).thenReturn(2);
        when(progress.getTotal()).thenReturn(3);
        when(progress.getElapsed()).thenReturn("0:05");

        ResponseEntity<TrackProcessingProgressDTO> response = controller.getProgress(5L);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        TrackProcessingProgressDTO body = response.getBody();
        assertEquals(5L, body.batchId());
        assertEquals(2, body.processed());
        assertEquals(3, body.total());
        assertEquals("0:05", body.elapsed());
    }
}
