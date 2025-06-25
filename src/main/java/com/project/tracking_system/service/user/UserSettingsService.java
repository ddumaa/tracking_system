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
    private final UserRepository userRepository;

    /**
     * Получить настройки пользователя. Если они отсутствуют, создаются настройки по умолчанию.
     *
     * @param userId идентификатор пользователя
     * @return найденные или созданные настройки
     */
    @Transactional
    public UserSettings getUserSettings(Long userId) {
        UserSettings settings = settingsRepository.findByUserId(userId);
        if (settings == null) {
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new IllegalArgumentException("Пользователь не найден"));
            settings = new UserSettings();
            settings.setUser(user);
            settings = settingsRepository.save(settings);
            log.info("Созданы настройки по умолчанию для пользователя {}", userId);
        }
        return settings;
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
