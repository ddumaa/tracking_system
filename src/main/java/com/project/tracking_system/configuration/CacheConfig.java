package com.project.tracking_system.configuration;

import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Конфигурация кэширования на основе встроенного ConcurrentMapCacheManager.
 * <p>
 * Выделяем отдельный бин, чтобы централизованно управлять кэшем и
 * придерживаться принципа единственной ответственности (SRP).
 * </p>
 */
@Configuration
public class CacheConfig {

    /**
     * Создаёт менеджер кэшей приложения.
     * <p>
     * Пока используется только кэш «track-details», однако при необходимости
     * конфигурацию можно расширить без изменения существующей логики
     * (принцип OCP).
     * </p>
     *
     * @return менеджер кэшей Spring
     */
    @Bean
    public CacheManager cacheManager() {
        return new ConcurrentMapCacheManager("track-details");
    }
}

