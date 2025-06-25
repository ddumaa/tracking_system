package com.project.tracking_system.service.user;

import com.project.tracking_system.entity.User;
import com.project.tracking_system.entity.UserSettings;
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
     * Проверить, включены ли уведомления Telegram для пользователя.
     *
     * @param userId идентификатор пользователя
     * @return {@code true}, если включены
     */
    @Transactional(readOnly = true)
    public boolean isTelegramNotificationsEnabled(Long userId) {
        UserSettings settings = getUserSettings(userId);
        return settings == null || settings.isTelegramNotificationsEnabled();
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

    /**
     * Обновить глобальный флаг Telegram-уведомлений.
     *
     * @param userId   идентификатор пользователя
     * @param enabled  новое значение
     */
    @Transactional
    public void updateTelegramNotificationsEnabled(Long userId, boolean enabled) {
        UserSettings settings = getUserSettings(userId);
        settings.setTelegramNotificationsEnabled(enabled);
        settingsRepository.save(settings);
        log.info("Флаг telegramNotificationsEnabled обновлен для пользователя {}: {}", userId, enabled);
    }

}