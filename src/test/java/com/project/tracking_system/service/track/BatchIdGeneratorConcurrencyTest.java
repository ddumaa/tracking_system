package com.project.tracking_system.service.track;

import com.project.tracking_system.controller.WebSocketController;
import com.project.tracking_system.dto.TrackProcessingProgressDTO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.verify;

/**
 * Проверяет, что при одновременном запуске нескольких партий
 * генератор выдаёт уникальные идентификаторы, а прогресс
 * по каждой партии завершается корректно.
 */
@ExtendWith(MockitoExtension.class)
class BatchIdGeneratorConcurrencyTest {

    @Mock
    private WebSocketController webSocketController;

    @Test
    void concurrentBatchesReceiveUniqueIdsAndFinishProgress() throws Exception {
        BatchIdGenerator generator = new BatchIdGenerator();
        ProgressAggregatorService aggregator = new ProgressAggregatorService(webSocketController, Clock.systemUTC(), 0L);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            CountDownLatch ready = new CountDownLatch(2);
            CountDownLatch start = new CountDownLatch(1);
            List<Long> batchIds = Collections.synchronizedList(new ArrayList<>());
            List<Long> users = List.of(101L, 202L);

            for (Long userId : users) {
                executor.submit(() -> {
                    ready.countDown();
                    try {
                        start.await();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException("Ожидание запуска партий было прервано", e);
                    }
                    long batchId = generator.nextId();
                    batchIds.add(batchId);
                    aggregator.registerBatch(batchId, 1, userId);
                    aggregator.trackProcessed(batchId);
                    return null;
                });
            }

            ready.await();
            start.countDown();
            executor.shutdown();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS), "Параллельные задачи должны завершиться вовремя");

            Set<Long> unique = new HashSet<>(batchIds);
            assertEquals(users.size(), unique.size(), "Каждая партия должна получить уникальный batchId");

            ArgumentCaptor<Long> userCaptor = ArgumentCaptor.forClass(Long.class);
            ArgumentCaptor<TrackProcessingProgressDTO> progressCaptor = ArgumentCaptor.forClass(TrackProcessingProgressDTO.class);
            verify(webSocketController, atLeast(users.size() * 2)).sendProgress(userCaptor.capture(), progressCaptor.capture());

            for (Long userId : users) {
                boolean hasFinal = false;
                List<Long> capturedUsers = userCaptor.getAllValues();
                List<TrackProcessingProgressDTO> progresses = progressCaptor.getAllValues();
                for (int i = 0; i < capturedUsers.size(); i++) {
                    if (userId.equals(capturedUsers.get(i))) {
                        TrackProcessingProgressDTO dto = progresses.get(i);
                        if (dto.total() == 1 && dto.processed() == 1) {
                            hasFinal = true;
                            break;
                        }
                    }
                }
                assertTrue(hasFinal, "Пользователь " + userId + " должен получить финальный прогресс 1/1");
            }
        } finally {
            executor.shutdownNow();
        }
    }
}

