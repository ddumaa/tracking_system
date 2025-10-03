package com.project.tracking_system.service.telegram;

import com.project.tracking_system.entity.BuyerBotScreen;
import com.project.tracking_system.entity.BuyerChatState;
import com.project.tracking_system.repository.BuyerAnnouncementStateRepository;
import com.project.tracking_system.repository.BuyerBotScreenStateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.time.ZonedDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Тесты репозитория сохранения сессий, проверяющие работу с объявлениями.
 */
@DataJpaTest
class DatabaseChatSessionRepositoryTest {

    @Autowired
    private BuyerBotScreenStateRepository screenStateRepository;

    @Autowired
    private BuyerAnnouncementStateRepository announcementStateRepository;

    private DatabaseChatSessionRepository repository;

    @BeforeEach
    void setUp() {
        repository = new DatabaseChatSessionRepository(screenStateRepository, announcementStateRepository);
    }

    /**
     * Проверяет, что новые поля объявления сохраняются и считываются вместе с сессией.
     */
    @Test
    void shouldPersistAnnouncementFields() {
        Long chatId = 101L;
        ChatSession session = new ChatSession(chatId, BuyerChatState.AWAITING_CONTACT, 123,
                BuyerBotScreen.MENU, false, false);
        session.setCurrentNotificationId(77L);
        session.setAnnouncementAnchorMessageId(555);
        session.setAnnouncementSeen(false);
        ZonedDateTime updatedAt = ZonedDateTime.now().minusHours(1).withNano(0);
        session.setAnnouncementUpdatedAt(updatedAt);

        repository.save(session);

        ChatSession loaded = repository.find(chatId).orElseThrow();
        assertEquals(77L, loaded.getCurrentNotificationId(), "Идентификатор объявления должен сохраняться");
        assertEquals(555, loaded.getAnnouncementAnchorMessageId(),
                "Якорное сообщение объявления должно считываться из БД");
        assertFalse(loaded.isAnnouncementSeen(), "Признак просмотра не должен устанавливаться автоматически");
        assertEquals(updatedAt, loaded.getAnnouncementUpdatedAt(),
                "Отметка времени последнего обновления объявления должна сохраняться");
    }

    /**
     * Проверяет изменение признака просмотра объявления через специализированные методы репозитория.
     */
    @Test
    void shouldMarkAnnouncementSeen() {
        Long chatId = 202L;
        ZonedDateTime updatedAt = ZonedDateTime.now().minusMinutes(5).withNano(0);
        repository.updateAnnouncement(chatId, 88L, 501, updatedAt);
        assertFalse(repository.isAnnouncementSeen(chatId),
                "После установки объявления оно не должно считаться просмотренным");

        repository.markAnnouncementSeen(chatId);

        assertTrue(repository.isAnnouncementSeen(chatId),
                "После подтверждения флаг просмотра должен быть установлен");

        ChatSession loaded = repository.find(chatId).orElseThrow();
        assertTrue(loaded.isAnnouncementSeen(), "Сохранённая сессия должна отражать признак просмотра");
        assertEquals(88L, loaded.getCurrentNotificationId(),
                "У сессии должен сохраняться идентификатор текущего объявления");
        assertEquals(501, loaded.getAnnouncementAnchorMessageId(),
                "Должен сохраняться идентификатор сообщения с объявлением");
        assertEquals(updatedAt, loaded.getAnnouncementUpdatedAt(),
                "Должна сохраняться отметка времени обновления объявления");
    }

    /**
     * Проверяет, что отметка просмотра объявления не сбрасывает якорь и создаёт запись для нового чата.
     */
    @Test
    void shouldSetAnnouncementAsSeenWithoutResettingAnchor() {
        Long chatId = 303L;
        ZonedDateTime initialUpdatedAt = ZonedDateTime.now().minusMinutes(20).withNano(0);
        repository.updateAnnouncement(chatId, 90L, 702, initialUpdatedAt);

        ZonedDateTime seenAt = initialUpdatedAt.plusMinutes(1);
        repository.setAnnouncementAsSeen(chatId, 90L, seenAt);

        ChatSession loaded = repository.find(chatId).orElseThrow();
        assertEquals(90L, loaded.getCurrentNotificationId(),
                "После фиксации просмотра должен сохраняться идентификатор объявления");
        assertEquals(702, loaded.getAnnouncementAnchorMessageId(),
                "Якорное сообщение не должно сбрасываться при отметке просмотра");
        assertTrue(loaded.isAnnouncementSeen(),
                "Отметка просмотра обязана сохраняться в сессии после фиксации");
        assertEquals(seenAt, loaded.getAnnouncementUpdatedAt(),
                "Должно сохраняться время актуального объявления после фиксации просмотра");

        Long freshChatId = 404L;
        ZonedDateTime freshSeenAt = ZonedDateTime.now().minusMinutes(2).withNano(0);
        repository.setAnnouncementAsSeen(freshChatId, 91L, freshSeenAt);

        ChatSession freshSession = repository.find(freshChatId).orElseThrow();
        assertEquals(91L, freshSession.getCurrentNotificationId(),
                "Для нового чата должен сохраняться идентификатор активного объявления");
        assertNull(freshSession.getAnnouncementAnchorMessageId(),
                "Без показанного баннера якорь должен оставаться пустым");
        assertTrue(freshSession.isAnnouncementSeen(),
                "Новому чату после отметки просмотренным объявление должно считаться изученным");
        assertEquals(freshSeenAt, freshSession.getAnnouncementUpdatedAt(),
                "Для нового чата должно сохраняться время последнего обновления объявления");
    }

    /**
     * Проверяет сохранение временных данных сценария возврата.
     */
    @Test
    void shouldPersistReturnFlowDraft() {
        Long chatId = 505L;
        ChatSession session = new ChatSession(chatId, BuyerChatState.AWAITING_RETURN_REASON, 321,
                BuyerBotScreen.MENU, true, true);
        session.setReturnParcelId(777L);
        session.setReturnParcelTrackNumber("TRACK-777");
        session.setReturnReason("Не подошёл размер");
        session.setReturnIdempotencyKey("draft-key");

        repository.save(session);

        ChatSession restored = repository.find(chatId).orElseThrow();
        assertEquals(777L, restored.getReturnParcelId(), "Идентификатор посылки должен сохраняться");
        assertEquals("TRACK-777", restored.getReturnParcelTrackNumber(),
                "Трек исходной посылки должен восстанавливаться");
        assertEquals("Не подошёл размер", restored.getReturnReason(),
                "Причина возврата обязана сохраняться");
        assertEquals("draft-key", restored.getReturnIdempotencyKey(),
                "Идемпотентный ключ должен сохраняться вместе с сессией");
    }
}

