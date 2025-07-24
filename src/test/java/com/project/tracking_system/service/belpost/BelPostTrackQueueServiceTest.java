package com.project.tracking_system.service.belpost;

import com.project.tracking_system.dto.TrackInfoDTO;
import com.project.tracking_system.dto.TrackInfoListDTO;
import com.project.tracking_system.dto.BelPostBatchFinishedDTO;
import com.project.tracking_system.controller.WebSocketController;
import com.project.tracking_system.service.track.TrackProcessingService;
import com.project.tracking_system.service.track.ProgressAggregatorService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.openqa.selenium.WebDriverException;
import org.springframework.scheduling.annotation.Scheduled;

import java.lang.reflect.Field;
import java.util.List;
import java.time.Duration;
import com.project.tracking_system.service.belpost.QueuedTrack;
import com.project.tracking_system.service.track.TrackSource;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import org.mockito.ArgumentCaptor;
import static org.mockito.Mockito.*;

/**
 * Тесты для {@link BelPostTrackQueueService}.
 */
@ExtendWith({MockitoExtension.class, SpringExtension.class})
@ContextConfiguration(classes = BelPostTrackQueueServiceTest.PropertyConfig.class)
@TestPropertySource("classpath:application.properties")
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

    /** Значение задержки между обработками, извлечённое из конфигурации. */
    @Value("${belpost.queue.delay-ms}")
    private long configuredDelayMs;

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
     * Проверяем аннотацию планировщика: метод должен использовать значение
     * задержки из конфигурации приложения.
     */
    @Test
    void processQueue_HasConfigurableFixedDelay() throws Exception {
        Scheduled scheduled = BelPostTrackQueueService.class
                .getMethod("processQueue")
                .getAnnotation(Scheduled.class);
        assertNotNull(scheduled, "Аннотация @Scheduled отсутствует");
        assertEquals("${belpost.queue.delay-ms:100}", scheduled.fixedDelayString());
        assertEquals(100L, configuredDelayMs);
    }

    /**
     * Убеждаемся, что треки обрабатываются и прогресс по партии обновляется.
     */
    @Test
    void processQueue_UpdatesProgressAndSendsEvents() {
        TrackInfoListDTO dto = new TrackInfoListDTO();
        dto.addTrackInfo(new TrackInfoDTO("d", "info"));
        when(webBelPostBatchService.parseTrack(anyString())).thenReturn(dto);

        QueuedTrack t1 = new QueuedTrack("T1", 1L, 1L, TrackSource.MANUAL, 10L);
        QueuedTrack t2 = new QueuedTrack("T2", 1L, 1L, TrackSource.MANUAL, 10L);
        queueService.enqueue(List.of(t1, t2));

        // Обрабатываем первый трек и проверяем, что статистика партии корректно обновилась
        queueService.processQueue();
        BelPostTrackQueueService.BatchProgress progress = queueService.getProgress(10L);
        assertEquals(2, progress.getTotal());
        assertEquals(1, progress.getProcessed());
        assertEquals(1, progress.getSuccess());
        assertEquals(0, progress.getFailed());

        // Проверяем отправку событий о старте партии и обработке трека
        verify(webSocketController).sendBelPostBatchStarted(eq(1L), any());
        verify(webSocketController).sendBelPostTrackProcessed(eq(1L), argThat(dto ->
                "info".equals(dto.status()) && dto.completed() == 1 && dto.total() == 2));
        verify(progressAggregatorService).trackProcessed(10L);

        // Обработка второго трека приводит к завершению партии
        queueService.processQueue();
        assertNull(queueService.getProgress(10L));

        // Фиксируем отправленные данные о завершении и сравниваем время обработки
        ArgumentCaptor<BelPostBatchFinishedDTO> captor = ArgumentCaptor.forClass(BelPostBatchFinishedDTO.class);
        verify(webSocketController).sendBelPostBatchFinished(eq(1L), captor.capture());
        BelPostBatchFinishedDTO finishedDto = captor.getValue();
        assertEquals(progress.getElapsed(), finishedDto.elapsed());

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
        queueService.enqueue(new QueuedTrack("T1", 2L, 1L, TrackSource.MANUAL, 20L));

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
        queueService.enqueue(new QueuedTrack("T1", 1L, 1L, TrackSource.MANUAL, 10L));
        queueService.enqueue(new QueuedTrack("T2", 2L, 1L, TrackSource.MANUAL, 20L));

        assertEquals(Duration.ofSeconds(2), queueService.estimateWaitTime(2L));
    }

    private long getPauseUntil(BelPostTrackQueueService service) throws Exception {
        Field f = BelPostTrackQueueService.class.getDeclaredField("pauseUntil");
        f.setAccessible(true);
        return f.getLong(service);
    }

    /**
     * Конфигурация для загрузки свойств приложения в тесты.
     */
    @Configuration
    @PropertySource("classpath:application.properties")
    static class PropertyConfig {
        @Bean
        static PropertySourcesPlaceholderConfigurer propertySourcesPlaceholderConfigurer() {
            return new PropertySourcesPlaceholderConfigurer();
        }
    }
}
