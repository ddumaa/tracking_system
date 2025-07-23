package com.project.tracking_system.service.track;

import com.project.tracking_system.dto.TrackStatusUpdateDTO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Сервис-кэш для временного хранения результатов обработки по пользователям и партиям.
 * <p>
 * Используется для восстановления таблицы результатов после перезагрузки страницы.
 * Результаты группируются по идентификатору пользователя и идентификатору партии.
 * </p>
 */
@Service
public class TrackingResultCacheService {

    /** Время жизни результатов в кэше (мс). */
    private final long expirationMs;

    /**
     * Конструктор, принимающий значение таймаута из конфигурации.
     *
     * @param expirationMs время жизни данных в миллисекундах
     */
    public TrackingResultCacheService(
            @Value("${tracking.result-cache.expiration-ms:60000}") long expirationMs) {
        this.expirationMs = expirationMs;
    }

    /** Карта вида userId -> (batchId -> результаты с отметкой времени). */
    private final Map<Long, Map<Long, BatchEntry>> cache = new ConcurrentHashMap<>();

    /**
     * Добавляет один результат обработки в кэш.
     *
     * @param userId идентификатор пользователя
     * @param dto    результат обработки трека
     */
    public void addResult(Long userId, TrackStatusUpdateDTO dto) {
        if (userId == null || dto == null) {
            return;
        }
        cache
                .computeIfAbsent(userId, id -> new ConcurrentHashMap<>())
                .computeIfAbsent(dto.batchId(), id -> new BatchEntry())
                .add(dto);
    }

    /**
     * Возвращает сохранённые результаты для указанной партии пользователя.
     *
     * @param userId  идентификатор пользователя
     * @param batchId идентификатор партии
     * @return список результатов, может быть пустым
     */
    public List<TrackStatusUpdateDTO> getResults(Long userId, Long batchId) {
        if (userId == null || batchId == null) {
            return List.of();
        }
        Map<Long, BatchEntry> byBatch = cache.get(userId);
        if (byBatch == null) {
            return List.of();
        }
        BatchEntry entry = byBatch.get(batchId);
        return entry != null ? entry.snapshot() : List.of();
    }

    /**
     * Получает результаты последней партии пользователя.
     *
     * @param userId идентификатор пользователя
     * @return список результатов либо пустой список
     */
    public List<TrackStatusUpdateDTO> getLatestResults(Long userId) {
        Map<Long, BatchEntry> byBatch = cache.get(userId);
        if (byBatch == null || byBatch.isEmpty()) {
            return List.of();
        }
        Long latestBatchId = byBatch.keySet().stream().max(Long::compareTo).orElse(null);
        if (latestBatchId == null) {
            return List.of();
        }
        return getResults(userId, latestBatchId);
    }

    /**
     * Очищает кэш результатов пользователя.
     *
     * @param userId идентификатор пользователя
     */
    public void clearResults(Long userId) {
        if (userId != null) {
            cache.remove(userId);
        }
    }

    /**
     * Периодически удаляет устаревшие записи из кэша.
     * <p>Выполняется каждые 30 секунд.</p>
     */
    @Scheduled(fixedDelay = 30_000)
    public void removeExpired() {
        long threshold = System.currentTimeMillis() - expirationMs;
        cache.entrySet().removeIf(userEntry -> {
            Map<Long, BatchEntry> byBatch = userEntry.getValue();
            byBatch.entrySet().removeIf(e -> e.getValue().expired(threshold));
            return byBatch.isEmpty();
        });
    }

    /**
     * Контейнер для списка результатов одной партии и времени последнего доступа.
     */
    private static class BatchEntry {
        private final List<TrackStatusUpdateDTO> results = Collections.synchronizedList(new ArrayList<>());
        private volatile long lastAccess;

        BatchEntry() {
            refresh();
        }

        void add(TrackStatusUpdateDTO dto) {
            results.add(dto);
            refresh();
        }

        List<TrackStatusUpdateDTO> snapshot() {
            refresh();
            return new ArrayList<>(results);
        }

        void refresh() {
            lastAccess = System.currentTimeMillis();
        }

        boolean expired(long threshold) {
            return lastAccess < threshold;
        }
    }
}
