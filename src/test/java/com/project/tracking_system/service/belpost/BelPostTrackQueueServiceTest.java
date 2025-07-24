package com.project.tracking_system.service.belpost;

import com.project.tracking_system.dto.TrackInfoDTO;
import com.project.tracking_system.dto.TrackInfoListDTO;
import com.project.tracking_system.controller.WebSocketController;
import com.project.tracking_system.service.track.TrackProcessingService;
import com.project.tracking_system.service.track.ProgressAggregatorService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openqa.selenium.WebDriverException;
import org.springframework.scheduling.annotation.Scheduled;

import java.lang.reflect.Field;
import java.util.List;
import java.time.Duration;
import com.project.tracking_system.service.belpost.QueuedTrack;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

/**
 * Тесты для {@link BelPostTrackQueueService}.
 */
@ExtendWith(MockitoExtension.class)
class BelPostTrackQueueServiceTest {

    @Mock
    private WebBelPostBatchService webBelPostBatchService;
    @Mock
    private TrackProcessingService trackProcessingService;
    @Mock
    private WebSocketController webSocketController;
    @Mock
    private ProgressAggregatorService progressAggregatorService;

    private BelPostTrackQueueService queueService;

    @BeforeEach
    void setUp() {
        queueService = new BelPostTrackQueueService(
                webBelPostBatchService,
                trackProcessingService,
                webSocketController,
                progressAggregatorService
        );
    }

    /**
     * Проверяем аннотацию планировщика: метод должен выполняться практически
     * без паузы (минимальный {@code fixedDelay=1}).
     */
    @Test
    void processQueue_HasMinimalFixedDelay() throws Exception {
        Scheduled scheduled = BelPostTrackQueueService.class
                .getMethod("processQueue")
                .getAnnotation(Scheduled.class);
        assertNotNull(scheduled, "Аннотация @Scheduled отсутствует");
        assertEquals(1, scheduled.fixedDelay());
    }

    /**
     * Убеждаемся, что треки обрабатываются и прогресс по партии обновляется.
     */
    @Test
    void processQueue_UpdatesProgressAndSendsEvents() {
        TrackInfoListDTO dto = new TrackInfoListDTO();
        dto.addTrackInfo(new TrackInfoDTO("d", "info"));
        when(webBelPostBatchService.parseTrack(anyString())).thenReturn(dto);

        QueuedTrack t1 = new QueuedTrack("T1", 1L, 1L, "src", 10L);
        QueuedTrack t2 = new QueuedTrack("T2", 1L, 1L, "src", 10L);
        queueService.enqueue(List.of(t1, t2));

        queueService.processQueue();
        BelPostTrackQueueService.BatchProgress p = queueService.getProgress(10L);
        assertEquals(2, p.getTotal());
        assertEquals(1, p.getProcessed());
        assertEquals(1, p.getSuccess());
        assertEquals(0, p.getFailed());
        verify(webSocketController).sendBelPostBatchStarted(eq(1L), any());
        verify(webSocketController).sendBelPostTrackProcessed(eq(1L), argThat(dto ->
                "info".equals(dto.status()) && dto.completed() == 1 && dto.total() == 2));
        verify(progressAggregatorService).trackProcessed(10L);

        queueService.processQueue();
        assertNull(queueService.getProgress(10L));
        verify(webSocketController).sendBelPostBatchFinished(eq(1L), any());
        verify(progressAggregatorService, times(2)).trackProcessed(10L);
    }

    /**
     * При ошибке Selenium сервис должен приостановить обработку на минуту
     * и отправить сообщение пользователю.
     */
    @Test
    void processQueue_PausesOnWebDriverFailure() throws Exception {
        when(webBelPostBatchService.parseTrack(anyString()))
                .thenThrow(new WebDriverException("fail"));
        queueService.enqueue(new QueuedTrack("T1", 2L, 1L, "src", 20L));

        long before = System.currentTimeMillis();
        queueService.processQueue();

        long pauseUntil = getPauseUntil(queueService);
        assertTrue(pauseUntil >= before + 60_000L);
        verify(webSocketController)
                .sendUpdateStatus(2L, "Белпочта временно недоступна", false);

        verify(progressAggregatorService).trackProcessed(20L);

        BelPostTrackQueueService.BatchProgress p = queueService.getProgress(20L);
        assertEquals(1, p.getFailed());
    }

    @Test
    void estimateWaitTime_ReturnsDurationBasedOnPosition() {
        queueService.enqueue(new QueuedTrack("T1", 1L, 1L, "src", 10L));
        queueService.enqueue(new QueuedTrack("T2", 2L, 1L, "src", 20L));

        assertEquals(Duration.ofSeconds(2), queueService.estimateWaitTime(2L));
    }

    private long getPauseUntil(BelPostTrackQueueService service) throws Exception {
        Field f = BelPostTrackQueueService.class.getDeclaredField("pauseUntil");
        f.setAccessible(true);
        return f.getLong(service);
    }
}
