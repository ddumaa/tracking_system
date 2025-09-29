package com.project.tracking_system.service.telegram;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.project.tracking_system.entity.AdminNotification;
import com.project.tracking_system.repository.CustomerRepository;
import com.project.tracking_system.service.admin.AdminNotificationService;
import com.project.tracking_system.service.telegram.support.InMemoryChatSessionRepository;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Модульные тесты широковещательного сервиса рассылки административных объявлений.
 */
class TelegramAnnouncementBroadcasterTest {

    private AdminNotificationService adminNotificationService;
    private CustomerRepository customerRepository;
    private RecordingAnnouncementSender announcementSender;
    private InMemoryChatSessionRepository chatSessionRepository;
    private TelegramAnnouncementBroadcaster broadcaster;

    @BeforeEach
    void setUp() {
        adminNotificationService = mock(AdminNotificationService.class);
        customerRepository = mock(CustomerRepository.class);
        chatSessionRepository = new InMemoryChatSessionRepository();
        announcementSender = new RecordingAnnouncementSender(chatSessionRepository);
        broadcaster = new TelegramAnnouncementBroadcaster(adminNotificationService, customerRepository, announcementSender);
    }

    /**
     * Проверяет, что рассылка выполняется только по подтверждённым чатам и сбрасывает флаг просмотра.
     */
    @Test
    void shouldBroadcastToConfirmedChatsAndResetSeenFlag() {
        AdminNotification notification = new AdminNotification();
        notification.setId(55L);
        notification.setTitle("Важно");
        notification.setUpdatedAt(ZonedDateTime.now(ZoneOffset.UTC));

        when(adminNotificationService.findActiveNotification()).thenReturn(Optional.of(notification));
        when(customerRepository.findConfirmedTelegramChatIds()).thenReturn(List.of(101L, 202L));

        chatSessionRepository.markAnnouncementSeen(101L);
        chatSessionRepository.markAnnouncementSeen(202L);

        broadcaster.broadcastActiveAnnouncement(notification.getId());

        assertEquals(List.of(101L, 202L), announcementSender.getInvokedChatIds(),
                "Должны обрабатываться только подтверждённые чаты");
        assertFalse(chatSessionRepository.isAnnouncementSeen(101L),
                "После рассылки признак просмотра должен сбрасываться");
        assertFalse(chatSessionRepository.isAnnouncementSeen(202L),
                "После рассылки признак просмотра должен сбрасываться");
    }

    /**
     * Убеждается, что при отсутствии активного объявления рассылка не запускается.
     */
    @Test
    void shouldSkipBroadcastWhenNoActiveNotification() {
        when(adminNotificationService.findActiveNotification()).thenReturn(Optional.empty());

        broadcaster.broadcastActiveAnnouncement(99L);

        verifyNoInteractions(customerRepository);
        assertEquals(List.of(), announcementSender.getInvokedChatIds(),
                "Не должно быть вызовов рендеринга без активного уведомления");
    }

    /**
     * Записывает список обработанных чатов и обновляет состояние объявлений через репозиторий.
     */
    private static final class RecordingAnnouncementSender implements TelegramAnnouncementSender {

        private final InMemoryChatSessionRepository repository;
        private final List<Long> invokedChatIds = new ArrayList<>();

        private RecordingAnnouncementSender(InMemoryChatSessionRepository repository) {
            this.repository = repository;
        }

        @Override
        public void showActiveAnnouncement(Long chatId) {
            invokedChatIds.add(chatId);
            repository.updateAnnouncement(chatId, 777L, null, ZonedDateTime.now(ZoneOffset.UTC));
        }

        private List<Long> getInvokedChatIds() {
            return List.copyOf(invokedChatIds);
        }
    }
}
