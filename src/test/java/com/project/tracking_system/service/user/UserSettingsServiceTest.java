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

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
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
    void getUserSettings_SettingsMissing_CreatesDefault() {
        User user = new User();
        user.setId(1L);

        when(settingsRepository.findByUserId(1L)).thenReturn(null);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(settingsRepository.save(any(UserSettings.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        UserSettings result = service.getUserSettings(1L);

        assertNotNull(result);
        assertEquals(user, result.getUser());
        verify(settingsRepository).save(any(UserSettings.class));
    }

    @Test
    void getUserSettings_ExistingSettings_ReturnedAsIs() {
        UserSettings existing = new UserSettings();
        existing.setId(2L);

        when(settingsRepository.findByUserId(2L)).thenReturn(existing);

        UserSettings result = service.getUserSettings(2L);

        assertEquals(existing, result);
        verify(settingsRepository, never()).save(any());
        verify(userRepository, never()).findById(any());
    }
}
