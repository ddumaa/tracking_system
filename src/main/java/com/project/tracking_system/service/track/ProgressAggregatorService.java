package com.project.tracking_system.service.track;

import com.project.tracking_system.controller.WebSocketController;
import com.project.tracking_system.dto.TrackProcessingProgressDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

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

    /** Состояние прогресса для каждой партии. */
    private final Map<Long, BatchProgress> progressMap = new ConcurrentHashMap<>();

    /**
     * Регистрирует новую партию для отслеживания прогресса.
     *
     * @param batchId уникальный идентификатор партии
     * @param total   сколько треков нужно обработать
     * @param userId  идентификатор владельца партии
     */
    public void registerBatch(long batchId, int total, Long userId) {
        progressMap.put(batchId, new BatchProgress(total, userId));
        sendProgress(batchId);
    }

    /**
     * Помечает один трек в партии обработанным и отправляет событие прогресса.
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

    /** Отправляет текущий прогресс клиенту через WebSocket. */
    private void sendProgress(long batchId) {
        BatchProgress progress = progressMap.get(batchId);
        if (progress == null) {
            return;
        }
        webSocketController.sendProgress(progress.userId, buildDto(batchId, progress));
    }

    private TrackProcessingProgressDTO buildDto(long batchId, BatchProgress progress) {
        String elapsed = formatElapsed(progress.startTime);
        return new TrackProcessingProgressDTO(batchId, progress.processed.get(), progress.total, elapsed);
    }

    private static String formatElapsed(long start) {
        Duration d = Duration.ofMillis(System.currentTimeMillis() - start);
        return String.format("%d:%02d", d.toMinutes(), d.toSecondsPart());
    }

    /** Контейнер с счётчиками одной партии. */
    private static class BatchProgress {
        final int total;
        final AtomicInteger processed = new AtomicInteger();
        final long userId;
        final long startTime = System.currentTimeMillis();

        BatchProgress(int total, Long userId) {
            this.total = total;
            this.userId = userId != null ? userId : 0L;
        }
    }
}
