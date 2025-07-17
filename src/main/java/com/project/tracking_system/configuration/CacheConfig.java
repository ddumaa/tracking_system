package com.project.tracking_system.configuration;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.project.tracking_system.dto.TrackInfoListDTO;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/**
 * Конфигурация кэша для хранения результатов трекинга.
 */
@Configuration
public class CacheConfig {

    /**
     * Создаёт кэш для объектов {@link TrackInfoListDTO} с временем жизни записи
     * несколько минут. Максимальный размер кэша ограничен, чтобы предотвратить
     * избыточное потребление памяти.
     *
     * @return кэш, ключом которого является номер трека
     */
    @Bean
    public Cache<String, TrackInfoListDTO> trackInfoCache() {
        return Caffeine.newBuilder()
                .expireAfterWrite(5, TimeUnit.MINUTES)
                .maximumSize(1000)
                .build();
    }
}
