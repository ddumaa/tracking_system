package com.project.tracking_system.service.track;

import com.project.tracking_system.service.admin.ApplicationSettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Cache service for storing invalid track rows grouped by user and batch.
 * <p>
 * Allows restoring the table with errors after page reload.
 * Entries expire after a configurable TTL obtained from
 * {@link ApplicationSettingsService}.
 * </p>
 */
@Service
@RequiredArgsConstructor
public class InvalidTrackCacheService {

    /** Application settings provider. */
    private final ApplicationSettingsService applicationSettingsService;

    /** Map userId -> (batchId -> cached entry). */
    private final Map<Long, Map<Long, BatchEntry>> cache = new ConcurrentHashMap<>();

    /**
     * Stores invalid tracks for the specified batch.
     *
     * @param userId  owner identifier
     * @param batchId batch identifier
     * @param tracks  list of invalid tracks
     */
    public void addInvalidTracks(Long userId, Long batchId, List<InvalidTrack> tracks) {
        if (userId == null || batchId == null || tracks == null || tracks.isEmpty()) {
            return;
        }
        cache
                .computeIfAbsent(userId, id -> new ConcurrentHashMap<>())
                .computeIfAbsent(batchId, id -> new BatchEntry())
                .addAll(tracks);
    }

    /**
     * Returns invalid tracks for a user's batch.
     *
     * @param userId  user identifier
     * @param batchId batch identifier
     * @return list of invalid tracks or empty list
     */
    public List<InvalidTrack> getInvalidTracks(Long userId, Long batchId) {
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
     * Returns the invalid tracks from the latest batch of the user.
     *
     * @param userId user identifier
     * @return list of invalid tracks or empty list
     */
    public List<InvalidTrack> getLatestInvalidTracks(Long userId) {
        Map<Long, BatchEntry> byBatch = cache.get(userId);
        if (byBatch == null || byBatch.isEmpty()) {
            return List.of();
        }
        Long latestBatchId = byBatch.keySet().stream().max(Long::compareTo).orElse(null);
        if (latestBatchId == null) {
            return List.of();
        }
        return getInvalidTracks(userId, latestBatchId);
    }

    /**
     * Clears all cached invalid tracks of the user.
     *
     * @param userId identifier of the user
     */
    public void clearInvalidTracks(Long userId) {
        if (userId != null) {
            cache.remove(userId);
        }
    }

    /**
     * Removes expired cache entries based on configured TTL.
     * Executed every 30 seconds.
     */
    @Scheduled(fixedDelay = 30_000)
    public void removeExpired() {
        long expiration = applicationSettingsService.getResultCacheExpirationMs();
        long threshold = System.currentTimeMillis() - expiration;
        cache.entrySet().removeIf(userEntry -> {
            Map<Long, BatchEntry> byBatch = userEntry.getValue();
            byBatch.entrySet().removeIf(e -> e.getValue().expired(threshold));
            return byBatch.isEmpty();
        });
    }

    /**
     * Container storing invalid tracks of a batch and last access time.
     */
    private static class BatchEntry {
        /** Stored invalid tracks. */
        private final List<InvalidTrack> tracks = Collections.synchronizedList(new ArrayList<>());
        /** Last access timestamp. */
        private volatile long lastAccess;

        BatchEntry() {
            refresh();
        }

        void addAll(List<InvalidTrack> list) {
            tracks.addAll(list);
            refresh();
        }

        List<InvalidTrack> snapshot() {
            refresh();
            return new ArrayList<>(tracks);
        }

        void refresh() {
            lastAccess = System.currentTimeMillis();
        }

        boolean expired(long threshold) {
            return lastAccess < threshold;
        }
    }
}

