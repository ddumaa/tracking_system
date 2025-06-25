package com.project.tracking_system.service.user;

import com.project.tracking_system.entity.User;
import com.project.tracking_system.entity.UserSettings;
import com.project.tracking_system.repository.UserRepository;
import com.project.tracking_system.repository.UserSettingsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Сервис управления пользовательскими настройками.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UserSettingsService {

    private final UserSettingsRepository settingsRepository;

    /**
     * Получить настройки пользователя. Если они отсутствуют, возвращается null.
     */
    @Transactional(readOnly = true)
    public UserSettings getUserSettings(Long userId) {
        return settingsRepository.findByUserId(userId);
    }

    /**
     * Обновить видимость кнопки массового обновления.
     *
     * @param userId идентификатор пользователя
     * @param value  новое значение флага
     */
    @Transactional
    public void updateShowBulkUpdateButton(Long userId, boolean value) {
        UserSettings settings = getUserSettings(userId);
        settings.setShowBulkUpdateButton(value);
        settingsRepository.save(settings);
        log.info("Настройка showBulkUpdateButton обновлена для пользователя {}: {}", userId, value);
    }

}