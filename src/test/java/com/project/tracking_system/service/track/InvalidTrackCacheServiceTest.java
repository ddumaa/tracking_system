package com.project.tracking_system.service.track;

import com.project.tracking_system.service.admin.ApplicationSettingsService;
import com.project.tracking_system.service.track.InvalidTrackReason;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests for {@link InvalidTrackCacheService}.
 */
@ExtendWith(MockitoExtension.class)
class InvalidTrackCacheServiceTest {

    @Mock
    private ApplicationSettingsService applicationSettingsService;

    private InvalidTrackCacheService service;

    @BeforeEach
    void setUp() {
        // создаём сервис перед каждым тестом, передавая мок настроек
        service = new InvalidTrackCacheService(applicationSettingsService);
    }

    /**
     * Проверяет, что очистка кэша учитывает обновлённое значение TTL.
     * TTL берётся из {@link ApplicationSettingsService} каждый раз при вызове {@link InvalidTrackCacheService#removeExpired()}.
     */
    @Test
    void removeExpired_RespectsUpdatedSetting() {
        // сначала возвращаем большое значение TTL, затем обнуляем его
        when(applicationSettingsService.getResultCacheExpirationMs()).thenReturn(100L, 0L);

        service.addInvalidTracks(1L, 1L, List.of(new InvalidTrack("A", InvalidTrackReason.WRONG_FORMAT)));
        // первое обращение к кэшу запускает отсчёт времени жизни
        service.getInvalidTracks(1L, 1L);

        // при ненулевом TTL запись должна оставаться в кэше
        service.removeExpired();
        assertFalse(service.getInvalidTracks(1L, 1L).isEmpty());

        try {
            Thread.sleep(1);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
        // после смены TTL на ноль запись должна быть удалена
        service.removeExpired();
        List<InvalidTrack> remaining = service.getInvalidTracks(1L, 1L);
        assertTrue(remaining.isEmpty(), "Cache entry should be removed after TTL becomes zero");

        verify(applicationSettingsService, times(2)).getResultCacheExpirationMs();
    }

    /**
     * Проверяет, что возвращается список последнего загруженного батча.
     */
    @Test
    void getLatestInvalidTracks_ReturnsNewestBatch() {
        service.addInvalidTracks(1L, 1L, List.of(new InvalidTrack("A", InvalidTrackReason.EMPTY_NUMBER)));
        service.addInvalidTracks(1L, 2L, List.of(new InvalidTrack("B", InvalidTrackReason.DUPLICATE)));

        List<InvalidTrack> list = service.getLatestInvalidTracks(1L);

        assertEquals(1, list.size());
        assertEquals("B", list.get(0).number());
    }

    /**
     * Убеждаемся, что запись не удаляется, пока пользователь не откроет список ошибок.
     */
    @Test
    void notViewed_EntriesIgnoredUntilFirstAccess() {
        when(applicationSettingsService.getResultCacheExpirationMs()).thenReturn(0L);

        service.addInvalidTracks(1L, 1L, List.of(new InvalidTrack("A", InvalidTrackReason.DUPLICATE)));
        service.removeExpired();
        assertFalse(service.getInvalidTracks(1L, 1L).isEmpty(), "Cache should persist until viewed");

        // первое обращение помечает запись просмотренной
        service.getInvalidTracks(1L, 1L);
        service.removeExpired();
        assertTrue(service.getInvalidTracks(1L, 1L).isEmpty(), "Cache should expire after viewing when TTL elapsed");
    }

}