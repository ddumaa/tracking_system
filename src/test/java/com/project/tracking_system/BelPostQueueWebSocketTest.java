package com.project.tracking_system;

import com.project.tracking_system.controller.WebSocketController;
import com.project.tracking_system.dto.TrackInfoDTO;
import com.project.tracking_system.dto.TrackInfoListDTO;
import com.project.tracking_system.service.belpost.BelPostTrackQueueService;
import com.project.tracking_system.service.belpost.QueuedTrack;
import com.project.tracking_system.service.track.TrackSource;
import com.project.tracking_system.service.belpost.WebBelPostBatchService;
import com.project.tracking_system.service.track.ProgressAggregatorService;
import com.project.tracking_system.service.track.TrackProcessingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.BDDMockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.socket.WebSocketTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.support.ExecutorSubscribableChannel;
import org.springframework.test.context.ContextConfiguration;

import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;

/**
 * Проверяет отправку сообщений WebSocket при обработке очереди Белпочты.
 */
@WebSocketTest(controllers = WebSocketController.class)
@Import({BelPostTrackQueueService.class, ProgressAggregatorService.class})
class BelPostQueueWebSocketTest {

    @Autowired
    private BelPostTrackQueueService queueService;

    @Autowired
    private MessageChannel clientOutboundChannel;

    @MockBean
    private WebBelPostBatchService webBelPostBatchService;

    @MockBean
    private TrackProcessingService trackProcessingService;

    private BlockingQueue<Message<?>> messages;

    @BeforeEach
    void setUp() {
        messages = new LinkedBlockingQueue<>();
        ((ExecutorSubscribableChannel) clientOutboundChannel).subscribe(messages::add);

        TrackInfoListDTO dto = new TrackInfoListDTO();
        dto.addTrackInfo(new TrackInfoDTO("2024-01-01", "OK"));
        BDDMockito.given(webBelPostBatchService.parseTrack(anyString())).willReturn(dto);
    }

    /**
     * Обрабатывает один трек и убеждается, что отправлены сообщения о прогрессе и результате.
     */
    @Test
    void processQueueSendsProgressAndTrackProcessed() {
        queueService.enqueue(new QueuedTrack("RR123", 1L, 1L, TrackSource.EXCEL, 1L));

        queueService.processQueue();

        List<Object> destinations = messages.stream()
                .map(m -> m.getHeaders().get(SimpMessageHeaderAccessor.DESTINATION_HEADER))
                .toList();

        assertThat(destinations)
                .contains("/topic/belpost/track-processed/1", "/topic/progress/1");
    }
}
