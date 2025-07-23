package com.project.tracking_system.service.track;

import com.project.tracking_system.dto.TrackStatusUpdateDTO;
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

    /** Карта вида userId -> (batchId -> список результатов). */
    private final Map<Long, Map<Long, List<TrackStatusUpdateDTO>>> cache = new ConcurrentHashMap<>();

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
                .computeIfAbsent(dto.batchId(), id -> Collections.synchronizedList(new ArrayList<>()))
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
        Map<Long, List<TrackStatusUpdateDTO>> byBatch = cache.get(userId);
        if (byBatch == null) {
            return List.of();
        }
        List<TrackStatusUpdateDTO> list = byBatch.get(batchId);
        return list != null ? new ArrayList<>(list) : List.of();
    }

    /**
     * Получает результаты последней партии пользователя.
     *
     * @param userId идентификатор пользователя
     * @return список результатов либо пустой список
     */
    public List<TrackStatusUpdateDTO> getLatestResults(Long userId) {
        Map<Long, List<TrackStatusUpdateDTO>> byBatch = cache.get(userId);
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
}
