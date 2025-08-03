package com.project.tracking_system.service.ratelimit;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Простая реализация {@link RateLimiter}, хранящая счётчики запросов в памяти.
 * <p>
 * Используется для ограничения количества обращений с одного IP-адреса
 * за заданный временной промежуток.
 * </p>
 */
@Service
@RequiredArgsConstructor
public class InMemoryRateLimiter implements RateLimiter {

    /** Максимальное количество запросов в окне. */
    @Value("${contact.rate-limit.max:5}")
    private int maxRequests;

    /** Длительность окна в миллисекундах. */
    @Value("${contact.rate-limit.window-ms:60000}")
    private long windowMs;

    /** Хранилище информации о запросах по ключу. */
    private final Map<String, RequestInfo> requests = new ConcurrentHashMap<>();

    @Override
    public boolean isAllowed(String key) {
        long now = System.currentTimeMillis();
        RequestInfo info = requests.computeIfAbsent(key, k -> new RequestInfo(now));
        synchronized (info) {
            if (now - info.windowStart > windowMs) {
                info.count = 0;
                info.windowStart = now;
            }
            if (info.count >= maxRequests) {
                return false;
            }
            info.count++;
            return true;
        }
    }

    /**
     * Структура данных для хранения состояния запросов.
     */
    private static class RequestInfo {
        /** Время начала окна. */
        long windowStart;
        /** Количество запросов в текущем окне. */
        int count;

        RequestInfo(long windowStart) {
            this.windowStart = windowStart;
        }
    }
}
