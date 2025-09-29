package com.project.tracking_system.service.track;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Плановый очиститель кэша деталей трека.
 * <p>
 * Компонент изолирован от основной логики сервисов, чтобы соблюсти принципы
 * SRP и DIP: {@link TrackViewService} не зависит от конкретного механизма
 * инвалидации кэша и оперирует только абстракцией {@link CacheManager}.
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TrackViewCacheInvalidator {

    private static final String CACHE_NAME = "track-details";

    /** Менеджер кэшей приложения. */
    private final CacheManager cacheManager;

    /**
     * Периодически очищает кэш деталей, чтобы данные не устаревали.
     * <p>
     * Интервал 8 секунд подобран как компромисс между нагрузкой на БД и
     * своевременным обновлением данных модального окна.
     * </p>
     */
    @Scheduled(fixedDelay = 8_000)
    public void evictTrackDetails() {
        Cache cache = cacheManager.getCache(CACHE_NAME);
        if (cache != null) {
            cache.clear();
            log.trace("Очистка кэша {} выполнена", CACHE_NAME);
        }
    }
}

