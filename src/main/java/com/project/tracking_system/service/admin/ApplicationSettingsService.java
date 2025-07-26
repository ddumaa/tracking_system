package com.project.tracking_system.service.admin;

import com.project.tracking_system.entity.ApplicationSettings;
import com.project.tracking_system.repository.ApplicationSettingsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Сервис управления глобальными настройками приложения.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ApplicationSettingsService {

    /**
     * Идентификатор единственной записи с настройками.
     */
    public static final long SETTINGS_ID = 1L;

    private final ApplicationSettingsRepository repository;

    /** Значение TTL кэша по умолчанию (мс). Один час. */
    public static final long DEFAULT_CACHE_EXPIRATION_MS = 3_600_000L;

    /**
     * Получить текущий интервал автообновления треков в часах.
     * Если запись отсутствует, возвращается значение по умолчанию.
     */
    @Transactional(readOnly = true)
    public int getTrackUpdateIntervalHours() {
        return repository.findById(SETTINGS_ID)
                .map(ApplicationSettings::getTrackUpdateIntervalHours)
                .orElse(3); // значение по умолчанию
    }

    /**
     * Обновить интервал автообновления треков.
     *
     * @param hours новое значение интервала в часах. Значение должно быть больше нуля,
     *              иначе будет сгенерировано {@link IllegalArgumentException}.
     * @throws IllegalArgumentException если значение {@code hours} не положительно
     */
    @Transactional
    public void updateTrackUpdateIntervalHours(int hours) {
        if (hours <= 0) {
            throw new IllegalArgumentException("Интервал должен быть положительным");
        }
        ApplicationSettings settings = repository
                .findById(SETTINGS_ID)
                .orElseGet(() -> {
                    ApplicationSettings s = new ApplicationSettings();
                    s.setId(SETTINGS_ID);
                    return s;
                });
        settings.setTrackUpdateIntervalHours(hours);
        repository.save(settings);
        log.info("Интервал автообновления треков изменён на {} ч", hours);
    }

    /**
     * Получить время хранения результатов в кэше.
     * Возвращает значение по умолчанию, если запись не найдена.
     */
    @Transactional(readOnly = true)
    public long getResultCacheExpirationMs() {
        return repository.findById(SETTINGS_ID)
                .map(ApplicationSettings::getResultCacheExpirationMs)
                .orElse(DEFAULT_CACHE_EXPIRATION_MS);
    }

    /**
     * Обновить TTL кэша результатов.
     *
     * @param ms новое значение TTL в миллисекундах, должно быть положительным
     * @throws IllegalArgumentException если значение {@code ms} не положительно
     */
    @Transactional
    public void updateResultCacheExpirationMs(long ms) {
        if (ms <= 0) {
            throw new IllegalArgumentException("TTL должен быть положительным");
        }
        ApplicationSettings settings = repository
                .findById(SETTINGS_ID)
                .orElseGet(() -> {
                    ApplicationSettings s = new ApplicationSettings();
                    s.setId(SETTINGS_ID);
                    return s;
                });
        settings.setResultCacheExpirationMs(ms);
        repository.save(settings);
        log.info("Время жизни кэша результатов изменено на {} мс", ms);
    }
}
