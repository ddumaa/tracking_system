package com.project.tracking_system.service.user;

import com.project.tracking_system.entity.UserSettings;
import com.project.tracking_system.repository.UserSettingsRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Тесты для {@link UserSettingsService}.
 */
@ExtendWith(MockitoExtension.class)
class UserSettingsServiceTest {

    @Mock
    private UserSettingsRepository settingsRepository;

    @InjectMocks
    private UserSettingsService service;

    @Test
    void isTelegramNotificationsEnabled_ReturnsValue() {
        UserSettings settings = new UserSettings();
        settings.setTelegramNotificationsEnabled(false);
        when(settingsRepository.findByUserId(1L)).thenReturn(settings);

        boolean result = service.isTelegramNotificationsEnabled(1L);

        assertFalse(result);
    }

    @Test
    void updateTelegramNotificationsEnabled_UpdatesFlag() {
        UserSettings settings = new UserSettings();
        when(settingsRepository.findByUserId(2L)).thenReturn(settings);

        service.updateTelegramNotificationsEnabled(2L, true);

        assertTrue(settings.isTelegramNotificationsEnabled());
        verify(settingsRepository).save(settings);
    }
}
