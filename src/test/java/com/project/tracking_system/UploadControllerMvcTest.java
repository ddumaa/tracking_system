package com.project.tracking_system;

import com.project.tracking_system.controller.UploadController;
import com.project.tracking_system.controller.WebSocketController;
import com.project.tracking_system.dto.TrackExcelRow;
import com.project.tracking_system.dto.TrackInfoDTO;
import com.project.tracking_system.dto.TrackInfoListDTO;
import com.project.tracking_system.service.belpost.BelPostTrackQueueService;
import com.project.tracking_system.service.belpost.WebBelPostBatchService;
import com.project.tracking_system.service.store.StoreService;
import com.project.tracking_system.service.track.ProgressAggregatorService;
import com.project.tracking_system.service.track.TrackExcelParser;
import com.project.tracking_system.service.track.TrackProcessingService;
import com.project.tracking_system.service.track.TrackUploadProcessorService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.BDDMockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.support.ExecutorSubscribableChannel;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * Проверяет загрузку файла через контроллер и появление WebSocket-сообщений о прогрессе.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc(addFilters = false)
@Import({TrackUploadProcessorService.class, BelPostTrackQueueService.class,
        ProgressAggregatorService.class, UploadController.class, WebSocketController.class})
class UploadControllerMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private BelPostTrackQueueService queueService;

    @Autowired
    private MessageChannel clientOutboundChannel;

    @MockBean
    private TrackExcelParser parser;

    @MockBean
    private StoreService storeService;

    @MockBean
    private WebBelPostBatchService webBelPostBatchService;

    @MockBean
    private TrackProcessingService trackProcessingService;

    private BlockingQueue<Message<?>> messages;

    @BeforeEach
    void setUp() throws Exception {
        messages = new LinkedBlockingQueue<>();
        ((ExecutorSubscribableChannel) clientOutboundChannel).subscribe(messages::add);

        BDDMockito.given(storeService.getDefaultStoreId(anyLong())).willReturn(1L);

        List<TrackExcelRow> rows = List.of(new TrackExcelRow("RR123", "1", null));
        BDDMockito.given(parser.parse(any())).willReturn(rows);

        TrackInfoListDTO dto = new TrackInfoListDTO();
        dto.addTrackInfo(new TrackInfoDTO("2024-01-01", "OK"));
        BDDMockito.given(webBelPostBatchService.parseTrack(anyString())).willReturn(dto);
    }

    /**
     * Загружает Excel-файл и проверяет появление сообщений о прогрессе.
     */
    @Test
    void uploadFileTriggersProgressUpdates() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "tracks.xlsx",
                MediaType.APPLICATION_OCTET_STREAM_VALUE, new byte[0]);

        mockMvc.perform(multipart("/app/upload").file(file).param("storeId", "1"))
                .andExpect(status().isOk())
                .andExpect(view().name("app/home"));

        queueService.processQueue();

        assertThat(messages)
                .extracting(m -> m.getHeaders().get(SimpMessageHeaderAccessor.DESTINATION_HEADER))
                .contains("/topic/progress/1");
    }
}
