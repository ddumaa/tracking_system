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

    /**
     * Получить текущий интервал автообновления треков в часах.
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
     * @param hours новое значение интервала в часах
     */
    @Transactional
    public void updateTrackUpdateIntervalHours(int hours) {
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
}
