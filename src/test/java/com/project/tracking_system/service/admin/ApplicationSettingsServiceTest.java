package com.project.tracking_system.service.admin;

import com.project.tracking_system.repository.ApplicationSettingsRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Проверки {@link ApplicationSettingsService}.
 */
@ExtendWith(MockitoExtension.class)
class ApplicationSettingsServiceTest {

    @Mock
    private ApplicationSettingsRepository repository;

    @InjectMocks
    private ApplicationSettingsService service;

    @Test
    void updateTrackUpdateIntervalHours_ZeroValue_ThrowsException() {
        assertThrows(IllegalArgumentException.class,
                () -> service.updateTrackUpdateIntervalHours(0));
        verify(repository, never()).save(any());
    }

    @Test
    void updateTrackUpdateIntervalHours_NegativeValue_ThrowsException() {
        assertThrows(IllegalArgumentException.class,
                () -> service.updateTrackUpdateIntervalHours(-5));
        verify(repository, never()).save(any());
    }
}
