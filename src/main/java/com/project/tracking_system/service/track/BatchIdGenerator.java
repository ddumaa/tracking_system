package com.project.tracking_system.service.track;

import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Генерирует уникальные идентификаторы партий обработки треков.
 * <p>
 * Класс потокобезопасен и использует атомарный счётчик, чтобы
 * исключить повторное использование идентификаторов в параллельных
 * сценариях запуска партий.
 * </p>
 */
@Component
public class BatchIdGenerator {

    /** Последовательно увеличивающийся счётчик идентификаторов. */
    private final AtomicLong sequence = new AtomicLong(System.currentTimeMillis());

    /**
     * Возвращает следующий уникальный идентификатор партии.
     * <p>
     * Значение гарантированно растёт даже при одновременных обращениях
     * из разных потоков: если системное время не увеличилось, используется
     * следующий номер в последовательности.
     * </p>
     *
     * @return уникальное значение batchId
     */
    public long nextId() {
        return sequence.updateAndGet(current -> {
            long now = System.currentTimeMillis();
            long candidate = Math.max(now, current + 1);
            if (candidate == Long.MIN_VALUE) {
                return 0L;
            }
            return candidate;
        });
    }
}

