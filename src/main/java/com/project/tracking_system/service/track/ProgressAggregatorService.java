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
 * Aggregates progress from different track processing services and
 * sends unified progress updates to the client via {@link WebSocketController}.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ProgressAggregatorService {

    private final WebSocketController webSocketController;

    /** Progress state for each batch. */
    private final Map<Long, BatchProgress> progressMap = new ConcurrentHashMap<>();

    /**
     * Registers a new batch for progress tracking.
     *
     * @param batchId unique batch identifier
     * @param total   total tracks to process
     * @param userId  identifier of the user that owns the batch
     */
    public void registerBatch(long batchId, int total, Long userId) {
        progressMap.put(batchId, new BatchProgress(total, userId));
        sendProgress(batchId);
    }

    /**
     * Marks one track in the batch as processed and emits a progress event.
     *
     * @param batchId identifier of the batch
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
     * Returns current progress for the requested batch.
     *
     * @param batchId batch identifier
     * @return DTO with progress data
     */
    public TrackProcessingProgressDTO getProgress(long batchId) {
        BatchProgress progress = progressMap.get(batchId);
        if (progress == null) {
            return new TrackProcessingProgressDTO(batchId, 0, 0, "0:00");
        }
        return buildDto(batchId, progress);
    }

    /** Sends current progress to the client via WebSocket. */
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

    /** Container with counters for a single batch. */
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
