package com.project.tracking_system.service.track;

import com.project.tracking_system.dto.TrackStatusUpdateDTO;
import com.project.tracking_system.service.admin.ApplicationSettingsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Тесты для {@link TrackingResultCacheService}.
 */
@ExtendWith(MockitoExtension.class)
class TrackingResultCacheServiceTest {

    @Mock
    private ApplicationSettingsService applicationSettingsService;

    private TrackingResultCacheService service;

    @BeforeEach
    void setUp() {
        service = new TrackingResultCacheService(applicationSettingsService);
    }

    @Test
    void removeExpired_RespectsUpdatedSetting() {
        // Устанавливаем TTL 100 мс — запись не должна удаляться до просмотра
        when(applicationSettingsService.getResultCacheExpirationMs()).thenReturn(100L);

        service.addResult(1L, new TrackStatusUpdateDTO(1L, "A1", "ok", 1, 1));
        service.removeExpired();
        assertFalse(service.getResults(1L, 1L).isEmpty());

        // Меняем TTL на 0 мс. После просмотра запись должна удалиться
        when(applicationSettingsService.getResultCacheExpirationMs()).thenReturn(0L);
        service.getResults(1L, 1L); // помечает запись как просмотренную
        service.removeExpired();
        assertTrue(service.getResults(1L, 1L).isEmpty());

        // Значение TTL запрашивается при каждом запуске очистки
        verify(applicationSettingsService, times(2)).getResultCacheExpirationMs();
    }

    @Test
    void notViewed_EntriesIgnoredUntilFirstAccess() {
        when(applicationSettingsService.getResultCacheExpirationMs()).thenReturn(0L);

        service.addResult(1L, new TrackStatusUpdateDTO(1L, "A1", "ok", 1, 1));
        service.removeExpired();
        assertFalse(service.getResults(1L, 1L).isEmpty(), "Кэш должен сохраняться до первого просмотра");

        // Первый доступ помечает запись как просмотренную, после чего она должна удалиться
        service.getResults(1L, 1L);
        service.removeExpired();
        assertTrue(service.getResults(1L, 1L).isEmpty(), "Кэш должен удаляться после просмотра при истекшем TTL");
    }
}
