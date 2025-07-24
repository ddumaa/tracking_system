package com.project.tracking_system.service.track;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Тесты приватной логики формирования пользовательских сообщений
 * в {@link TrackUpdateService}.
 */
class TrackUpdateServiceTest {

    /**
     * Проверяет формирование сообщения при отсутствии треков, готовых к обновлению.
     */
    @Test
    void buildUpdateMessage_NoReadyTracks() {
        TrackUpdateService service = new TrackUpdateService(
                null, null, null, null, null,
                null, null, null, null, null,
                null, null);

        String msg = ReflectionTestUtils.invokeMethod(
                service, "buildUpdateMessage", 0, 2, 3);

        assertEquals(
                "Обновление не выполнено.\n- 2 треков уже в финальном статусе\n- 3 треков недавно обновлялись и пропущены",
                msg);
        assertFalse(msg.contains("\u25AA"));
        assertFalse(msg.contains("\uD83D"));
    }

    /**
     * Проверяет формирование сообщения когда часть треков доступна для обновления.
     */
    @Test
    void buildUpdateMessage_WithReadyTracks() {
        TrackUpdateService service = new TrackUpdateService(
                null, null, null, null, null,
                null, null, null, null, null,
                null, null);

        String msg = ReflectionTestUtils.invokeMethod(
                service, "buildUpdateMessage", 2, 1, 1);

        assertEquals(
                "Запущено обновление 2 из 4 треков\n- 1 треков уже в финальном статусе\n- 1 треков недавно обновлялись и пропущены",
                msg);
        assertFalse(msg.contains("\u25AA"));
        assertFalse(msg.contains("\uD83D"));
    }
}