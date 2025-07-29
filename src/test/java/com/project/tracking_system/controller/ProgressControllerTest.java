package com.project.tracking_system.controller;

import com.project.tracking_system.dto.TrackProcessingProgressDTO;
import com.project.tracking_system.service.track.ProgressAggregatorService;
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
    private ProgressAggregatorService progressAggregatorService;

    @InjectMocks
    private ProgressController controller;

    @Test
    void getProgress_ReturnsDto() {
        TrackProcessingProgressDTO dto = new TrackProcessingProgressDTO(5L, 2, 3, "0:05");
        when(progressAggregatorService.getProgress(5L)).thenReturn(dto);

        ResponseEntity<TrackProcessingProgressDTO> response = controller.getProgress(5L);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        TrackProcessingProgressDTO body = response.getBody();
        assertEquals(dto, body);
    }
}
