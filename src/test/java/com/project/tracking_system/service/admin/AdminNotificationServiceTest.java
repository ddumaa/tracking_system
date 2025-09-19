package com.project.tracking_system.service.admin;

import com.project.tracking_system.entity.AdminNotification;
import com.project.tracking_system.entity.AdminNotificationStatus;
import com.project.tracking_system.entity.BuyerAnnouncementState;
import com.project.tracking_system.repository.AdminNotificationRepository;
import com.project.tracking_system.repository.BuyerAnnouncementStateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.time.ZonedDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Интеграционные тесты сервиса административных уведомлений, проверяющие сброс просмотров баннеров.
 */
@DataJpaTest
class AdminNotificationServiceTest {

    @Autowired
    private AdminNotificationRepository notificationRepository;

    @Autowired
    private BuyerAnnouncementStateRepository announcementStateRepository;

    private AdminNotificationService notificationService;

    @BeforeEach
    void setUp() {
        notificationService = new AdminNotificationService(notificationRepository, announcementStateRepository);
    }

    /**
     * Убеждается, что запрос сброса очищает просмотры и снимает флаг повторного показа.
     */
    @Test
    void shouldResetAnnouncementViewsAndClearRequestFlag() {
        AdminNotification notification = new AdminNotification();
        notification.setTitle("Обновление условий");
        notification.setStatus(AdminNotificationStatus.ACTIVE);
        notification.setResetRequested(true);
        notification = notificationRepository.save(notification);
        ZonedDateTime initialUpdatedAt = notification.getUpdatedAt();

        BuyerAnnouncementState firstState = new BuyerAnnouncementState();
        firstState.setChatId(101L);
        firstState.setCurrentNotificationId(notification.getId());
        firstState.setAnnouncementSeen(true);
        firstState.setNotificationUpdatedAt(initialUpdatedAt.minusHours(2));
        announcementStateRepository.save(firstState);

        BuyerAnnouncementState secondState = new BuyerAnnouncementState();
        secondState.setChatId(202L);
        secondState.setCurrentNotificationId(notification.getId());
        secondState.setAnnouncementSeen(true);
        secondState.setNotificationUpdatedAt(initialUpdatedAt.minusHours(3));
        announcementStateRepository.save(secondState);

        notificationService.requestReset(notification.getId());

        List<BuyerAnnouncementState> states = announcementStateRepository.findAllByCurrentNotificationId(notification.getId());
        assertEquals(2, states.size(), "Должны обновиться состояния всех покупателей, получивших объявление");

        states.forEach(state -> {
            assertFalse(Boolean.TRUE.equals(state.getAnnouncementSeen()),
                    "После сброса объявление не должно считаться просмотренным");
            assertNotNull(state.getNotificationUpdatedAt(),
                    "Дата обновления баннера должна быть установлена после сброса");
            assertFalse(state.getNotificationUpdatedAt().isBefore(initialUpdatedAt),
                    "Время последнего обновления должно быть не раньше исходного значения");
        });

        AdminNotification reloaded = notificationRepository.findById(notification.getId()).orElseThrow();
        assertFalse(reloaded.isResetRequested(), "Флаг запроса повтора должен быть снят после обработки");
        assertTrue(reloaded.getUpdatedAt().isAfter(initialUpdatedAt),
                "Обновление уведомления должно изменить отметку времени модификации");
    }
}
