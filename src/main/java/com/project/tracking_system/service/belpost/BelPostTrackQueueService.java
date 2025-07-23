package com.project.tracking_system.service.belpost;

import com.project.tracking_system.controller.WebSocketController;
import com.project.tracking_system.dto.TrackInfoListDTO;
import com.project.tracking_system.dto.BelPostBatchStartedDTO;
import com.project.tracking_system.dto.TrackStatusUpdateDTO;
import com.project.tracking_system.dto.BelPostBatchFinishedDTO;
import com.project.tracking_system.service.track.TrackProcessingService;
import com.project.tracking_system.service.track.TrackConstants;
import com.project.tracking_system.service.track.ProgressAggregatorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.WebDriverException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.time.Duration;
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

    private final WebBelPostBatchService webBelPostBatchService;
    private final TrackProcessingService trackProcessingService;
    private final WebSocketController webSocketController;
    /** Aggregates overall progress from different services. */
    private final ProgressAggregatorService progressAggregatorService;

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
     * Периодически извлекает из очереди один трек и обрабатывает его.
     * Между итерациями выдерживается пауза 15 секунд.
     * После обработки каждого трека пользователю отправляется
     * обновление прогресса через WebSocket.
     */
    @Scheduled(fixedDelay = 0)
    public void processQueue() {
        if (Instant.now().toEpochMilli() < pauseUntil) {
            return; // временно приостановлено
        }

        QueuedTrack task = queue.poll();
        if (task == null) {
            return; // очередь пуста
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
            info = webBelPostBatchService.parseTrack(task.trackNumber());
            if (!info.getList().isEmpty()) {
                trackProcessingService.save(task.trackNumber(), info, task.storeId(), task.userId());
                progress.success.incrementAndGet();
            } else {
                progress.failed.incrementAndGet();
            }
        } catch (WebDriverException e) {
            log.error("\uD83D\uDEA7 Ошибка Selenium при обработке {}: {}", task.trackNumber(), e.getMessage());
            progress.failed.incrementAndGet();
            pauseUntil = Instant.now().plusSeconds(60).toEpochMilli();
            webSocketController.sendUpdateStatus(task.userId(), "Белпочта временно недоступна", false);
        } catch (Exception e) {
            log.error("\u274C Не удалось обработать {}: {}", task.trackNumber(), e.getMessage());
            progress.failed.incrementAndGet();
        }

        String status = info.getList().isEmpty()
                ? TrackConstants.NO_DATA_STATUS
                : info.getList().get(0).getInfoTrack();
        webSocketController.sendBelPostTrackProcessed(
                task.userId(),
                new TrackStatusUpdateDTO(
                        task.batchId(),
                        task.trackNumber(),
                        status,
                        progress.getProcessed(),
                        progress.getTotal()));

        progressAggregatorService.trackProcessed(task.batchId());

        if (currentProcessed >= progress.getTotal()) {
            webSocketController.sendBelPostBatchFinished(
                    task.userId(),
                    new BelPostBatchFinishedDTO(
                            task.batchId(),
                            progress.getProcessed(),
                            progress.getSuccess(),
                            progress.getFailed()));
            progressMap.remove(task.batchId());
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
         * Возвращает строковое представление прошедшего времени с начала партии.
         */
        public String getElapsed() {
            Duration d = Duration.ofMillis(System.currentTimeMillis() - startTime);
            return String.format("%d:%02d", d.toMinutes(), d.toSecondsPart());
        }
    }
}
