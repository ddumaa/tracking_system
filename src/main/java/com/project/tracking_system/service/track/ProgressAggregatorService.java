package com.project.tracking_system.service.track;

import com.project.tracking_system.controller.WebSocketController;
import com.project.tracking_system.dto.TrackProcessingProgressDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Сервис агрегирует прогресс обработки треков от различных подсервисов
 * и отправляет единые обновления клиенту через {@link WebSocketController}.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ProgressAggregatorService {

    private final WebSocketController webSocketController;
    /** Поставщик времени для вычисления длительности операций. */
    private final Clock clock;

    /** Состояние прогресса для каждой партии. */
    private final Map<Long, BatchProgress> progressMap = new ConcurrentHashMap<>();
    /** Время последней отправки прогресса по каждой партии. */
    private final Map<Long, Long> lastSentTimestamps = new ConcurrentHashMap<>();

    /** Минимальный интервал между отправками одного и того же прогресса (мс). */
    private static final long UPDATE_INTERVAL_MS = 250L;

    /**
     * Регистрирует новую партию для отслеживания прогресса.
     *
     * @param batchId уникальный идентификатор партии
     * @param total   сколько треков нужно обработать
     * @param userId  идентификатор владельца партии
     */
    public void registerBatch(long batchId, int total, Long userId) {
        progressMap.put(batchId, new BatchProgress(total, userId, clock));
        // сразу отправляем начальный прогресс (0 из total)
        sendProgress(batchId);
    }

    /**
     * Помечает один трек обработанным и при необходимости уведомляет клиента.
     * <p>
     * После достижения общего количества треков партия удаляется из отслеживания,
     * а финальный прогресс отправляется без задержки.
     * </p>
     *
     * @param batchId идентификатор партии
     */
    public void trackProcessed(long batchId) {
        BatchProgress progress = progressMap.get(batchId);
        if (progress == null) {
            return;
        }
        int processed = progress.processed.incrementAndGet();
        sendProgress(batchId);
        if (processed >= progress.total) {
            progressMap.remove(batchId);
            lastSentTimestamps.remove(batchId);
        }
    }

    /**
     * Возвращает текущий прогресс для указанной партии.
     *
     * @param batchId идентификатор партии
     * @return DTO с данными о прогрессе
     */
    public TrackProcessingProgressDTO getProgress(long batchId) {
        BatchProgress progress = progressMap.get(batchId);
        if (progress == null) {
            return new TrackProcessingProgressDTO(batchId, 0, 0, "0:00");
        }
        return buildDto(batchId, progress);
    }

    /**
     * Определяет идентификатор самой последней активной партии пользователя.
     * <p>
     * Если активных партий нет, возвращается {@code null}.
     * </p>
     *
     * @param userId идентификатор пользователя
     * @return id последней партии или {@code null}
     */
    public Long getLatestBatchId(Long userId) {
        return progressMap.entrySet().stream()
                .filter(e -> e.getValue().userId == (userId != null ? userId : 0L))
                .max((a, b) -> Long.compare(a.getValue().startTime, b.getValue().startTime))
                .map(Map.Entry::getKey)
                .orElse(null);
    }

    /**
     * Отправляет текущее состояние прогресса клиенту через WebSocket.
     * <p>
     * Обновления посылаются не чаще, чем раз в {@link #UPDATE_INTERVAL_MS} миллисекунд.
     * Финальное состояние отправляется незамедлительно.
     * </p>
     */
    private void sendProgress(long batchId) {
        BatchProgress progress = progressMap.get(batchId);
        if (progress == null) {
            return;
        }

        long now = clock.millis();
        boolean finished = progress.processed.get() >= progress.total;
        Long last = lastSentTimestamps.get(batchId);

        if (!finished && last != null && now - last < UPDATE_INTERVAL_MS) {
            return;
        }

        lastSentTimestamps.put(batchId, now);
        webSocketController.sendProgress(progress.userId, buildDto(batchId, progress));

        if (finished) {
            lastSentTimestamps.remove(batchId);
        }
    }

    private TrackProcessingProgressDTO buildDto(long batchId, BatchProgress progress) {
        String elapsed = formatElapsed(progress.startTime);
        return new TrackProcessingProgressDTO(batchId, progress.processed.get(), progress.total, elapsed);
    }

    private String formatElapsed(long start) {
        Duration d = Duration.ofMillis(clock.millis() - start);
        return String.format("%d:%02d", d.toMinutes(), d.toSecondsPart());
    }

    /**
     * Контейнер с счётчиками одной партии треков.
     * Инкапсулирует данные, необходимые для вычисления прогресса и времени.
     */
    private static class BatchProgress {
        final int total;
        final AtomicInteger processed = new AtomicInteger();
        final long userId;
        final long startTime;

        BatchProgress(int total, Long userId, Clock clock) {
            this.total = total;
            this.userId = userId != null ? userId : 0L;
            this.startTime = clock.millis();
        }
    }
}
