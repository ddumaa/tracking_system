package com.project.tracking_system.service.ratelimit;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Bucket4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Сервис ограничения частоты запросов на основе Bucket4j.
 * <p>
 * Поддерживает отдельные наборы лимитов для API покупателей и Telegram-хука,
 * что упрощает расширение и настройку.
 * </p>
 */
@Service
public class Bucket4jRateLimiter {

    /**
     * Хранилище бакетов для операций над покупателями.
     */
    private final Map<String, Bucket> customerBuckets = new ConcurrentHashMap<>();

    /**
     * Хранилище бакетов для Telegram-хука.
     */
    private final Map<String, Bucket> telegramBuckets = new ConcurrentHashMap<>();

    /**
     * Лимит для стандартных запросов.
     */
    private final Bandwidth customerLimit;

    /**
     * Лимит для Telegram-хука.
     */
    private final Bandwidth telegramLimit;

    /**
     * Создает сервис с настраиваемыми параметрами лимитов.
     *
     * @param customerCapacity максимальное количество запросов к API покупателей
     * @param customerInterval интервал восстановления лимита для покупателей
     * @param telegramCapacity максимальное количество запросов Telegram-хука
     * @param telegramInterval интервал восстановления лимита Telegram-хука
     */
    public Bucket4jRateLimiter(
            @Value("${rate-limit.customers.capacity:50}") long customerCapacity,
            @Value("${rate-limit.customers.interval-seconds:60}") long customerInterval,
            @Value("${rate-limit.telegram.capacity:100}") long telegramCapacity,
            @Value("${rate-limit.telegram.interval-seconds:60}") long telegramInterval
    ) {
        this.customerLimit = Bandwidth.simple(customerCapacity, Duration.ofSeconds(customerInterval));
        this.telegramLimit = Bandwidth.simple(telegramCapacity, Duration.ofSeconds(telegramInterval));
    }

    /**
     * Возвращает бакет для ключа покупателя, создавая его при необходимости.
     *
     * @param key идентификатор магазина или пользователя
     * @return бакет с лимитом для операций над покупателями
     */
    public Bucket resolveCustomerBucket(String key) {
        return customerBuckets.computeIfAbsent(key, k ->
                Bucket4j.builder().addLimit(customerLimit).build());
    }

    /**
     * Возвращает бакет для Telegram-хука.
     *
     * @param key произвольный ключ (например, IP источника)
     * @return бакет с лимитом для Telegram-хука
     */
    public Bucket resolveTelegramBucket(String key) {
        return telegramBuckets.computeIfAbsent(key, k ->
                Bucket4j.builder().addLimit(telegramLimit).build());
    }
}
