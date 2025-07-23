package com.project.tracking_system.service.belpost;

import com.project.tracking_system.dto.TrackInfoDTO;
import com.project.tracking_system.dto.TrackInfoListDTO;
import com.project.tracking_system.controller.WebSocketController;
import com.project.tracking_system.service.track.TrackProcessingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openqa.selenium.WebDriverException;
import org.springframework.scheduling.annotation.Scheduled;

import java.lang.reflect.Field;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
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

    private BelPostTrackQueueService queueService;

    @BeforeEach
    void setUp() {
        queueService = new BelPostTrackQueueService(
                webBelPostBatchService,
                trackProcessingService,
                webSocketController
        );
    }

    /**
     * Проверяем аннотацию планировщика: метод должен выполняться раз в 15 секунд.
     */
    @Test
    void processQueue_HasFixedDelayOf15Seconds() throws Exception {
        Scheduled scheduled = BelPostTrackQueueService.class
                .getMethod("processQueue")
                .getAnnotation(Scheduled.class);
        assertNotNull(scheduled, "Аннотация @Scheduled отсутствует");
        assertEquals(15000, scheduled.fixedDelay());
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
        verify(webSocketController).sendBelPostTrackProcessed(eq(1L), any());
        verify(webSocketController).sendProgress(eq(1L), any());

        queueService.processQueue();
        assertNull(queueService.getProgress(10L));
        verify(webSocketController).sendBelPostBatchFinished(eq(1L), any());
        verify(webSocketController, times(2)).sendProgress(eq(1L), any());
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

        BelPostTrackQueueService.BatchProgress p = queueService.getProgress(20L);
        assertEquals(1, p.getFailed());
    }

    private long getPauseUntil(BelPostTrackQueueService service) throws Exception {
        Field f = BelPostTrackQueueService.class.getDeclaredField("pauseUntil");
        f.setAccessible(true);
        return f.getLong(service);
    }
}
