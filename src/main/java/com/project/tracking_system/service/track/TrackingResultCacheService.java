package com.project.tracking_system.service.track;

import com.project.tracking_system.dto.TrackStatusUpdateDTO;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory cache for storing tracking results per user and batch.
 * <p>
 * The cache is used to restore the table of processed tracks on page reload.
 * Results are grouped by user id and batch id.
 * </p>
 */
@Service
public class TrackingResultCacheService {

    /** Map userId -&gt; (batchId -&gt; list of results). */
    private final Map<Long, Map<Long, List<TrackStatusUpdateDTO>>> cache = new ConcurrentHashMap<>();

    /**
     * Adds a single tracking result to the cache.
     *
     * @param userId identifier of the user
     * @param dto    result of track processing
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
     * Returns stored results for the given batch of the user.
     *
     * @param userId  user identifier
     * @param batchId batch identifier
     * @return list of results, possibly empty
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
     * Returns results of the latest batch for the user.
     *
     * @param userId user identifier
     * @return list of results or empty list if none
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
     * Clears cached results for a user.
     *
     * @param userId user identifier
     */
    public void clearResults(Long userId) {
        if (userId != null) {
            cache.remove(userId);
        }
    }
}
