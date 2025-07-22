package com.project.tracking_system.service.track;

import com.project.tracking_system.model.queue.QueuedTrack;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Простейшая очередь для треков Белпочты.
 * <p>
 * Треки добавляются в память и могут быть обработаны
 * асинхронным потребителем. Реализация минимальна и
 * служит демонстрационным примером.
 * </p>
 */
@Slf4j
@Service
public class BelPostTrackQueueService {

    /** Очередь треков, ожидающих обработки. */
    private final Queue<QueuedTrack> queue = new ConcurrentLinkedQueue<>();

    /**
     * Добавляет треки в очередь на обработку.
     *
     * @param tracks список треков
     */
    public void enqueue(List<QueuedTrack> tracks) {
        if (tracks == null || tracks.isEmpty()) {
            return;
        }
        queue.addAll(tracks);
        log.info("В очередь добавлено {} треков", tracks.size());
    }

    /**
     * Извлекает один элемент из очереди или возвращает {@code null}.
     */
    public QueuedTrack poll() {
        return queue.poll();
    }
}
