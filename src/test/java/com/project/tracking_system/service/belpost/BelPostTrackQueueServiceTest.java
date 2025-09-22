package com.project.tracking_system.service.belpost;

import com.project.tracking_system.controller.WebSocketController;
import com.project.tracking_system.dto.TrackInfoDTO;
import com.project.tracking_system.dto.TrackInfoListDTO;
import com.project.tracking_system.dto.BelPostBatchFinishedDTO;
import com.project.tracking_system.dto.TrackStatusUpdateDTO;
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
import java.util.List;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
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
    @Mock
    private WebDriver thirdDriver;

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

        setWebDriverMaxAttempts(3);
        when(webDriverFactory.create()).thenReturn(firstDriver, secondDriver, thirdDriver);
        when(webBelPostBatchService.parseTrack(any(WebDriver.class), eq(trackNumber)))
                .thenThrow(new WebDriverException("Временный сбой"))
                .thenThrow(new WebDriverException("Повторный сбой"))
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

        BelPostTrackQueueService.BatchProgress progressAfterSecondRetry = queueService.getProgress(batchId);
        assertNotNull(progressAfterSecondRetry, "Прогресс должен существовать после второго сбоя");
        assertThat(progressAfterSecondRetry.getFailed()).isZero();
        assertThat(progressAfterSecondRetry.getRetries()).isEqualTo(2);
        assertThat(progressAfterSecondRetry.getProcessed()).isZero();

        setPauseUntil(0L);

        queueService.processQueue();

        ArgumentCaptor<BelPostBatchFinishedDTO> finishedCaptor = ArgumentCaptor.forClass(BelPostBatchFinishedDTO.class);
        verify(webSocketController).sendBelPostBatchFinished(eq(track.userId()), finishedCaptor.capture());
        BelPostBatchFinishedDTO summary = finishedCaptor.getValue();
        assertThat(summary.failed()).isZero();
        assertThat(summary.retries()).isEqualTo(2);

        verify(webDriverFactory, times(3)).create();
    }

    /**
     * Проверяет, что при превышении лимита повторов трек помечается как окончательно проваленный.
     */
    @Test
    void processQueue_WebDriverExceptionExceedsLimitMarksAsFailed() throws Exception {
        String trackNumber = "BY987654321";
        long batchId = 13L;
        QueuedTrack track = new QueuedTrack(trackNumber, 21L, 8L, TrackSource.MANUAL, batchId, null);

        setWebDriverMaxAttempts(2);
        when(webDriverFactory.create()).thenReturn(firstDriver, secondDriver);
        when(webBelPostBatchService.parseTrack(any(WebDriver.class), eq(trackNumber)))
                .thenThrow(new WebDriverException("Сбой драйвера"))
                .thenThrow(new WebDriverException("Повторный сбой драйвера"));

        queueService.enqueue(track);

        queueService.processQueue();

        BelPostTrackQueueService.BatchProgress progressAfterFirstRetry = queueService.getProgress(batchId);
        assertNotNull(progressAfterFirstRetry, "Прогресс должен фиксироваться после первой ошибки");
        assertThat(progressAfterFirstRetry.getRetries()).isEqualTo(1);
        assertThat(progressAfterFirstRetry.getFailed()).isZero();
        assertThat(progressAfterFirstRetry.getProcessed()).isZero();

        setPauseUntil(0L);

        queueService.processQueue();

        ArgumentCaptor<TrackStatusUpdateDTO> statusCaptor = ArgumentCaptor.forClass(TrackStatusUpdateDTO.class);
        verify(webSocketController).sendBelPostTrackProcessed(eq(track.userId()), statusCaptor.capture());
        TrackStatusUpdateDTO failedStatus = statusCaptor.getValue();
        assertThat(failedStatus.status()).contains("превышен лимит попыток");
        assertThat(failedStatus.completed()).isEqualTo(1);
        assertThat(failedStatus.total()).isEqualTo(1);

        verify(trackingResultCacheService).addResult(eq(track.userId()), eq(failedStatus));
        verify(progressAggregatorService).trackProcessed(batchId);
        verifyNoInteractions(trackProcessingService);

        ArgumentCaptor<BelPostBatchFinishedDTO> finishedCaptor = ArgumentCaptor.forClass(BelPostBatchFinishedDTO.class);
        verify(webSocketController).sendBelPostBatchFinished(eq(track.userId()), finishedCaptor.capture());
        BelPostBatchFinishedDTO summary = finishedCaptor.getValue();
        assertThat(summary.failed()).isEqualTo(1);
        assertThat(summary.success()).isZero();
        assertThat(summary.retries()).isEqualTo(1);

        ArgumentCaptor<String> statusMessageCaptor = ArgumentCaptor.forClass(String.class);
        verify(webSocketController, times(2)).sendUpdateStatus(eq(track.userId()), statusMessageCaptor.capture(), eq(false));
        List<String> statusMessages = statusMessageCaptor.getAllValues();
        assertThat(statusMessages).hasSize(2);
        assertThat(statusMessages.get(1)).contains("превышен лимит попыток");

        verify(webBelPostBatchService, times(2)).parseTrack(any(WebDriver.class), eq(trackNumber));
        verify(webDriverFactory, times(2)).create();
        assertThat(queueService.getProgress(batchId)).isNull();
    }

    /**
     * Устанавливает максимальное число попыток обработки для проверки граничных сценариев.
     *
     * @param attempts желаемое значение лимита повторов
     */
    private void setWebDriverMaxAttempts(int attempts) throws Exception {
        Field attemptsField = BelPostTrackQueueService.class.getDeclaredField("webDriverMaxAttempts");
        attemptsField.setAccessible(true);
        attemptsField.set(queueService, attempts);
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
