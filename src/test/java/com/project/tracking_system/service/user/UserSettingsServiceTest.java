package com.project.tracking_system.service.user;

import com.project.tracking_system.entity.User;
import com.project.tracking_system.entity.UserSettings;
import com.project.tracking_system.repository.UserRepository;
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
    @Mock
    private UserRepository userRepository;

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

    @Test
    void getOrCreateSettings_ReturnsExisting() {
        UserSettings settings = new UserSettings();
        when(settingsRepository.findByUserId(3L)).thenReturn(settings);

        UserSettings result = service.getOrCreateSettings(3L);

        assertSame(settings, result);
        verify(settingsRepository, never()).save(any());
    }

    @Test
    void getOrCreateSettings_CreatesNew() {
        when(settingsRepository.findByUserId(4L)).thenReturn(null);
        User user = new User();
        user.setId(4L);
        when(userRepository.findById(4L)).thenReturn(java.util.Optional.of(user));
        when(settingsRepository.save(any(UserSettings.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserSettings result = service.getOrCreateSettings(4L);

        assertNotNull(result);
        assertEquals(user, result.getUser());
        verify(settingsRepository).save(result);
    }
}
