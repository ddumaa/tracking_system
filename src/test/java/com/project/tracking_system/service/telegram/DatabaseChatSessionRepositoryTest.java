package com.project.tracking_system.service.telegram;

import com.project.tracking_system.entity.BuyerBotScreen;
import com.project.tracking_system.entity.BuyerChatState;
import com.project.tracking_system.repository.BuyerAnnouncementStateRepository;
import com.project.tracking_system.repository.BuyerBotScreenStateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

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

        repository.save(session);

        ChatSession loaded = repository.find(chatId).orElseThrow();
        assertEquals(77L, loaded.getCurrentNotificationId(), "Идентификатор объявления должен сохраняться");
        assertEquals(555, loaded.getAnnouncementAnchorMessageId(),
                "Якорное сообщение объявления должно считываться из БД");
        assertFalse(loaded.isAnnouncementSeen(), "Признак просмотра не должен устанавливаться автоматически");
    }

    /**
     * Проверяет изменение признака просмотра объявления через специализированные методы репозитория.
     */
    @Test
    void shouldMarkAnnouncementSeen() {
        Long chatId = 202L;
        repository.updateAnnouncement(chatId, 88L, 501);
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
    }
}

