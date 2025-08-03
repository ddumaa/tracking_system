package com.project.tracking_system.service.ratelimit;

/**
 * Интерфейс сервиса ограничения частоты запросов.
 * <p>
 * Позволяет реализовать различные стратегии rate limiting,
 * сохраняя открытость для расширения (принцип OCP).
 * </p>
 */
public interface RateLimiter {

    /**
     * Проверяет, разрешён ли запрос для указанного ключа.
     *
     * @param key произвольный идентификатор (например, IP-адрес)
     * @return {@code true}, если лимит не превышен
     */
    boolean isAllowed(String key);
}
