package com.project.tracking_system.service.belpost;

import com.project.tracking_system.controller.WebSocketController;
import com.project.tracking_system.dto.TrackInfoListDTO;
import com.project.tracking_system.dto.BelPostBatchStartedDTO;
import com.project.tracking_system.dto.TrackStatusUpdateDTO;
import com.project.tracking_system.dto.BelPostBatchFinishedDTO;
import com.project.tracking_system.service.track.TrackProcessingService;
import com.project.tracking_system.service.track.TrackConstants;
import com.project.tracking_system.service.track.ProgressAggregatorService;
import com.project.tracking_system.service.track.TrackingResultCacheService;
import com.project.tracking_system.webdriver.WebDriverFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebDriverException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.time.Duration;
import com.project.tracking_system.utils.DurationUtils;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Очередь последовательной обработки трек-номеров Белпочты.
 * <p>
 * Предназначена для единообразного использования различными модулями
 * приложения: ручной ввод, импорт из Excel и автоматическое обновление.
 * Очередь обеспечивает потокобезопасность и ведёт статистику по каждой
 * партии треков.
 * </p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BelPostTrackQueueService {

    /** Среднее время обработки одного трека в секундах. */
    public static final long PROCESSING_DELAY_SECONDS = 2L;

    private final WebBelPostBatchService webBelPostBatchService;
    private final TrackProcessingService trackProcessingService;
    private final WebSocketController webSocketController;
    /** Сервис агрегирования прогресса из различных источников. */
    private final ProgressAggregatorService progressAggregatorService;
    /** Кэш результатов трекинга для восстановления состояния страницы. */
    private final TrackingResultCacheService trackingResultCacheService;
    /** Фабрика для создания экземпляров {@link WebDriver}. */
    private final WebDriverFactory webDriverFactory;

    /**
     * Общий браузер для последовательной обработки.
     * <p>Создаётся лениво и используется только потоком планировщика.
     * После опустошения очереди драйвер закрывается и ссылка обнуляется,
     * что предотвращает утечки ресурсов.</p>
     */
    private WebDriver sharedDriver;

    /** Хранилище заданий на обработку. */
    private final BlockingQueue<QueuedTrack> queue = new LinkedBlockingQueue<>();

    /** Прогресс по каждой пачке треков. */
    private final Map<Long, BatchProgress> progressMap = new ConcurrentHashMap<>();

    /** Время до которого обработка приостановлена (millis). */
    private volatile long pauseUntil = 0L;

    /** Добавляет один трек в очередь. */
    public void enqueue(QueuedTrack track) {
        if (track == null) {
            return;
        }
        queue.offer(track);
        BatchProgress progress = progressMap.computeIfAbsent(track.batchId(), id -> new BatchProgress());
        progress.total.incrementAndGet();
    }

    /** Добавляет список треков в очередь. */
    public void enqueue(List<QueuedTrack> tracks) {
        if (tracks == null || tracks.isEmpty()) {
            return;
        }
        tracks.forEach(this::enqueue);
    }

    /**
     * Возвращает текущий прогресс для указанной партии.
     *
     * @param batchId идентификатор партии
     * @return объект {@link BatchProgress} или {@code null}, если партия не найденa
     */
    public BatchProgress getProgress(long batchId) {
        return progressMap.get(batchId);
    }

    /**
     * Оценивает время ожидания до начала обработки следующего трека указанного пользователя.
     * <p>
     * Расчет основан на количестве задач в очереди перед первым треком пользователя
     * и средней задержке {@link #PROCESSING_DELAY_SECONDS} между обработками.
     * </p>
     *
     * @param userId идентификатор пользователя
     * @return примерная длительность ожидания
     */
    public Duration estimateWaitTime(Long userId) {
        if (userId == null) {
            return Duration.ZERO;
        }
        long ahead = queue.stream()
                .takeWhile(q -> !userId.equals(q.userId()))
                .count();
        return Duration.ofSeconds(ahead * PROCESSING_DELAY_SECONDS);
    }

    /**
     * Периодически извлекает из очереди один трек и обрабатывает его.
     * Метод вызывается практически без задержки, что обеспечивает
     * быструю обработку элементов очереди. После каждого трека
     * пользователю отправляется обновление прогресса через WebSocket.
     */
    /**
     * Минимальная задержка между итерациями в миллисекундах. Значение
     * считывается из конфигурации приложения и может быть изменено без
     * перекомпиляции.
     */
    @Scheduled(fixedDelayString = "${belpost.queue.delay-ms:100}")
    public void processQueue() {
        if (Instant.now().toEpochMilli() < pauseUntil) {
            return; // временно приостановлено
        }

        QueuedTrack task = queue.poll();
        if (task == null) {
            return; // очередь пуста
        }

        // Инициализируем браузер при первом запросе после простоя
        if (sharedDriver == null) {
            sharedDriver = webDriverFactory.create();
        }

        BatchProgress progress = progressMap.computeIfAbsent(task.batchId(), id -> new BatchProgress());
        int processedBefore = progress.processed.get();
        int currentProcessed = progress.processed.incrementAndGet();

        if (processedBefore == 0) {
            webSocketController.sendBelPostBatchStarted(
                    task.userId(),
                    new BelPostBatchStartedDTO(task.batchId(), progress.getTotal()));
        }

        TrackInfoListDTO info = new TrackInfoListDTO();
        try {
            info = webBelPostBatchService.parseTrack(sharedDriver, task.trackNumber());
            if (!info.getList().isEmpty()) {
                trackProcessingService.save(task.trackNumber(), info, task.storeId(), task.userId(), task.phone());
                progress.success.incrementAndGet();
            } else {
                progress.failed.incrementAndGet();
            }
        } catch (WebDriverException e) {
            log.error("\uD83D\uDEA7 Ошибка Selenium при обработке {}: {}", task.trackNumber(), e.getMessage());
            progress.failed.incrementAndGet();
            // закрываем неисправный драйвер, чтобы следующая задача создала новый экземпляр
            resetDriver();
            pauseUntil = Instant.now().plusSeconds(60).toEpochMilli();
            webSocketController.sendUpdateStatus(task.userId(), "Белпочта временно недоступна", false);
        } catch (Exception e) {
            log.error("\u274C Не удалось обработать {}: {}", task.trackNumber(), e.getMessage());
            progress.failed.incrementAndGet();
        }

        String status = info.getList().isEmpty()
                ? TrackConstants.NO_DATA_STATUS
                : info.getList().get(0).getInfoTrack();
        TrackStatusUpdateDTO dto = new TrackStatusUpdateDTO(
                task.batchId(),
                task.trackNumber(),
                status,
                progress.getProcessed(),
                progress.getTotal());
        webSocketController.sendBelPostTrackProcessed(task.userId(), dto);
        trackingResultCacheService.addResult(task.userId(), dto);

        progressAggregatorService.trackProcessed(task.batchId());

        if (currentProcessed >= progress.getTotal()) {
            webSocketController.sendBelPostBatchFinished(
                    task.userId(),
                    new BelPostBatchFinishedDTO(
                            task.batchId(),
                            progress.getProcessed(),
                            progress.getSuccess(),
                            progress.getFailed(),
                            progress.getElapsed()));
            progressMap.remove(task.batchId());
        }

        // После завершения обработки и опустошения очереди закрываем браузер
        if (queue.isEmpty()) {
            resetDriver();
        }
    }

    /**
     * Безопасно закрывает текущий {@link WebDriver} и освобождает ссылку.
     * <p>Метод вызывается после завершения пачки или при критической ошибке
     * Selenium, чтобы освободить ресурсы и обеспечить создание нового
     * экземпляра при следующей задаче.</p>
     */
    private void resetDriver() {
        if (sharedDriver != null) {
            try {
                sharedDriver.quit();
            } catch (Exception e) {
                log.warn("\u26A0\uFE0F Ошибка при закрытии WebDriver: {}", e.getMessage());
            } finally {
                sharedDriver = null;
            }
        }
    }

    /**
     * Контейнер статистики выполнения для одной партии треков.
     */
    public static class BatchProgress {
        private final AtomicInteger total = new AtomicInteger();
        private final AtomicInteger processed = new AtomicInteger();
        private final AtomicInteger success = new AtomicInteger();
        private final AtomicInteger failed = new AtomicInteger();
        /** Время начала обработки партии. */
        private final long startTime = System.currentTimeMillis();

        public int getTotal() {
            return total.get();
        }

        public int getProcessed() {
            return processed.get();
        }

        public int getSuccess() {
            return success.get();
        }

        public int getFailed() {
            return failed.get();
        }

        /**
         * Возвращает прошедшее время с начала обработки в формате mm:ss.
         */
        public String getElapsed() {
            Duration d = Duration.ofMillis(System.currentTimeMillis() - startTime);
            return DurationUtils.formatMinutesSeconds(d);
        }
    }
}
