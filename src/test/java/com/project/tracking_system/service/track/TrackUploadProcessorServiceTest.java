package com.project.tracking_system.service.track;

import com.project.tracking_system.controller.WebSocketController;
import com.project.tracking_system.service.track.BelPostTrackQueueService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TrackUploadProcessorServiceTest {

    @Mock
    private TrackExcelParser parser;
    @Mock
    private BelPostTrackQueueService queueService;
    @Mock
    private WebSocketController webSocketController;

    private TrackUploadProcessorService processor;

    @BeforeEach
    void setUp() {
        processor = new TrackUploadProcessorService(parser, queueService, webSocketController);
    }

    @Test
    void process_EnqueuesTracks() throws Exception {
        MockMultipartFile file = new MockMultipartFile("f", new byte[0]);
        when(parser.parse(file)).thenReturn(List.of(new TrackExcelRow("A1", "1", "p")));

        processor.process(file, 1L);

        verify(queueService).enqueue(anyList());
        verify(webSocketController).sendTrackProcessingStarted(eq(1L), any());
    }
}
