package com.project.tracking_system.utils;

import org.springframework.stereotype.Component;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Простая реализация {@link RateLimiter}, обеспечивающая старт не более
 * одного запроса каждые 2–3 секунды.
 * <p>
 * Следующий запрос откладывается на случайный интервал от двух до трёх секунд,
 * что позволяет избежать избыточной нагрузки на целевой сайт.
 * </p>
 */
@Component
public class SimpleRateLimiter implements RateLimiter {

    /** Минимальная задержка между запросами в миллисекундах (2 секунды). */
    private static final long MIN_INTERVAL_MS = 2000L;
    /** Максимальная задержка между запросами в миллисекундах (3 секунды). */
    private static final long MAX_INTERVAL_MS = 3000L;

    /** Временная метка, когда можно начинать следующий запрос. */
    private long nextAllowedTime = 0L;

    /**
     * Блокирует текущий поток до наступления разрешённого времени.
     *
     * @throws InterruptedException если поток был прерван во время ожидания
     */
    @Override
    public synchronized void acquire() throws InterruptedException {
        long now = System.currentTimeMillis();
        if (now < nextAllowedTime) {
            Thread.sleep(nextAllowedTime - now);
        }
        long delay = ThreadLocalRandom.current()
                .nextLong(MIN_INTERVAL_MS, MAX_INTERVAL_MS + 1);
        nextAllowedTime = System.currentTimeMillis() + delay;
    }
}
