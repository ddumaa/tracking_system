package com.project.tracking_system.service.belpost;

import com.project.tracking_system.controller.WebSocketController;
import com.project.tracking_system.dto.TrackInfoListDTO;
import com.project.tracking_system.dto.TrackInfoDTO;
import com.project.tracking_system.dto.BelPostBatchFinishedDTO;
import com.project.tracking_system.service.track.ProgressAggregatorService;
import com.project.tracking_system.service.track.TrackProcessingService;
import com.project.tracking_system.service.track.TrackSource;
import com.project.tracking_system.service.track.TrackingResultCacheService;
import com.project.tracking_system.webdriver.WebDriverFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebDriverException;

import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Набор тестов для {@link BelPostTrackQueueService}, проверяющий корректный учёт ошибок Selenium.
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
    @Mock
    private TrackingResultCacheService trackingResultCacheService;
    @Mock
    private WebDriverFactory webDriverFactory;
    @Mock
    private WebDriver firstDriver;
    @Mock
    private WebDriver secondDriver;

    private BelPostTrackQueueService queueService;

    /**
     * Подготавливает экземпляр сервиса и общие моки перед каждым тестом.
     */
    @BeforeEach
    void setUp() {
        queueService = new BelPostTrackQueueService(
                webBelPostBatchService,
                trackProcessingService,
                webSocketController,
                progressAggregatorService,
                trackingResultCacheService,
                webDriverFactory
        );
    }

    /**
     * Проверяет, что временные ошибки Selenium учитываются как повторные попытки,
     * не увеличивая счётчик окончательных неудач.
     */
    @Test
    void processQueue_TemporaryWebDriverErrorDoesNotIncrementFailed() throws Exception {
        String trackNumber = "BY123456789";
        long batchId = 7L;
        QueuedTrack track = new QueuedTrack(trackNumber, 11L, 5L, TrackSource.MANUAL, batchId, null);

        TrackInfoListDTO successInfo = new TrackInfoListDTO();
        successInfo.addTrackInfo(new TrackInfoDTO("2024-01-01", "Доставлено"));

        when(webDriverFactory.create()).thenReturn(firstDriver, secondDriver);
        when(webBelPostBatchService.parseTrack(any(WebDriver.class), eq(trackNumber)))
                .thenThrow(new WebDriverException("Временный сбой"))
                .thenReturn(successInfo);

        queueService.enqueue(track);

        queueService.processQueue();

        BelPostTrackQueueService.BatchProgress progressAfterRetry = queueService.getProgress(batchId);
        assertNotNull(progressAfterRetry, "Прогресс должен существовать после первого сбоя");
        assertThat(progressAfterRetry.getFailed()).isZero();
        assertThat(progressAfterRetry.getRetries()).isEqualTo(1);
        assertThat(progressAfterRetry.getProcessed()).isZero();

        setPauseUntil(0L);

        queueService.processQueue();

        ArgumentCaptor<BelPostBatchFinishedDTO> finishedCaptor = ArgumentCaptor.forClass(BelPostBatchFinishedDTO.class);
        verify(webSocketController).sendBelPostBatchFinished(eq(track.userId()), finishedCaptor.capture());
        BelPostBatchFinishedDTO summary = finishedCaptor.getValue();
        assertThat(summary.failed()).isZero();
        assertThat(summary.retries()).isEqualTo(1);

        verify(webDriverFactory, times(2)).create();
    }

    /**
     * Сбрасывает приватное поле pauseUntil, чтобы повторная попытка обработки была возможна в тестах.
     *
     * @param value новое значение таймера паузы в миллисекундах
     */
    private void setPauseUntil(long value) throws Exception {
        Field pauseField = BelPostTrackQueueService.class.getDeclaredField("pauseUntil");
        pauseField.setAccessible(true);
        pauseField.set(queueService, value);
    }
}
