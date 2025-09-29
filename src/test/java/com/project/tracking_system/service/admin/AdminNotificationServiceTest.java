package com.project.tracking_system.service.admin;

import com.project.tracking_system.entity.AdminNotification;
import com.project.tracking_system.entity.AdminNotificationStatus;
import com.project.tracking_system.entity.BuyerAnnouncementState;
import com.project.tracking_system.repository.AdminNotificationRepository;
import com.project.tracking_system.repository.BuyerAnnouncementStateRepository;
import com.project.tracking_system.service.admin.event.AdminAnnouncementEvent;
import com.project.tracking_system.service.admin.event.AdminAnnouncementEvent.AdminAnnouncementEventType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.ApplicationEventPublisher;

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

    private RecordingEventPublisher eventPublisher;

    @BeforeEach
    void setUp() {
        eventPublisher = new RecordingEventPublisher();
        notificationService = new AdminNotificationService(notificationRepository, announcementStateRepository, eventPublisher);
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

        AdminAnnouncementEvent lastEvent = eventPublisher.getLastEvent();
        assertNotNull(lastEvent, "После сброса должно публиковаться событие рассылки");
        assertEquals(AdminAnnouncementEventType.RESET_REQUESTED, lastEvent.type(),
                "Тип события при сбросе должен быть RESET_REQUESTED");
        assertEquals(notification.getId(), lastEvent.notificationId(),
                "В событии должен передаваться идентификатор уведомления");
    }

    /**
     * Проверяет, что активация уведомления публикует событие для рассылки.
     */
    @Test
    void shouldPublishEventOnActivation() {
        AdminNotification notification = new AdminNotification();
        notification.setTitle("Новости");
        notification = notificationRepository.save(notification);

        eventPublisher.clear();

        notificationService.activateNotification(notification.getId());

        AdminAnnouncementEvent lastEvent = eventPublisher.getLastEvent();
        assertNotNull(lastEvent, "При активации должно отправляться событие");
        assertEquals(AdminAnnouncementEventType.ACTIVATED, lastEvent.type(),
                "Тип события должен отражать активацию уведомления");
        assertEquals(notification.getId(), lastEvent.notificationId(),
                "В событии должен быть указан идентификатор активного уведомления");
    }

    /**
     * Простая реализация издателя событий, сохраняющая последнее опубликованное событие для проверок.
     */
    private static class RecordingEventPublisher implements ApplicationEventPublisher {

        private AdminAnnouncementEvent lastEvent;

        @Override
        public void publishEvent(Object event) {
            if (event instanceof AdminAnnouncementEvent announcementEvent) {
                this.lastEvent = announcementEvent;
            }
        }

        public AdminAnnouncementEvent getLastEvent() {
            return lastEvent;
        }

        public void clear() {
            this.lastEvent = null;
        }
    }
}
