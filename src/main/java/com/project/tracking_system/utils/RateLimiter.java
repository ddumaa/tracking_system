package com.project.tracking_system.utils;

/**
 * Интерфейс ограничения частоты запросов.
 */
public interface RateLimiter {
    /**
     * Блокирует выполнение пока не наступит разрешённый интервал.
     *
     * @throws InterruptedException если поток был прерван во время ожидания
     */
    void acquire() throws InterruptedException;
}
