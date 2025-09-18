package com.project.tracking_system.service.admin;

import com.project.tracking_system.entity.AdminNotification;
import com.project.tracking_system.entity.AdminNotificationStatus;
import com.project.tracking_system.repository.AdminNotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Сервис управления административными уведомлениями.
 */
@Service
@RequiredArgsConstructor
public class AdminNotificationService {

    private final AdminNotificationRepository notificationRepository;

    /**
     * Возвращает историю уведомлений в порядке создания.
     */
    @Transactional(readOnly = true)
    public List<AdminNotification> getHistory() {
        return notificationRepository.findAllByOrderByCreatedAtDesc();
    }

    /**
     * Получает уведомление по идентификатору.
     */
    @Transactional(readOnly = true)
    public AdminNotification getNotification(Long id) {
        return notificationRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Уведомление не найдено"));
    }

    /**
     * Создаёт новое уведомление и запрашивает его показ пользователям.
     */
    @Transactional
    public AdminNotification createNotification(String title, List<String> bodyLines) {
        AdminNotification notification = new AdminNotification();
        notification.setTitle(title);
        notification.setBodyLines(new ArrayList<>(bodyLines));
        notification.setStatus(AdminNotificationStatus.INACTIVE);
        notification.setResetRequested(true);
        return notificationRepository.save(notification);
    }

    /**
     * Ищет активное уведомление, которое должно отображаться пользователям.
     *
     * @return опциональное уведомление в статусе {@link AdminNotificationStatus#ACTIVE}
     */
    @Transactional(readOnly = true)
    public Optional<AdminNotification> findActiveNotification() {
        return notificationRepository.findFirstByStatus(AdminNotificationStatus.ACTIVE);
    }

    /**
     * Обновляет текстовое содержимое уведомления.
     */
    @Transactional
    public AdminNotification updateNotification(Long id, String title, List<String> bodyLines) {
        AdminNotification notification = getNotification(id);
        notification.setTitle(title);
        notification.setBodyLines(new ArrayList<>(bodyLines));
        return notification;
    }

    /**
     * Активирует выбранное уведомление и отключает предыдущее активное.
     */
    @Transactional
    public void activateNotification(Long id) {
        AdminNotification toActivate = getNotification(id);
        notificationRepository.findFirstByStatus(AdminNotificationStatus.ACTIVE)
                .filter(active -> !active.getId().equals(id))
                .ifPresent(active -> active.setStatus(AdminNotificationStatus.INACTIVE));
        toActivate.setStatus(AdminNotificationStatus.ACTIVE);
        toActivate.setResetRequested(true);
    }

    /**
     * Переводит уведомление в неактивное состояние.
     */
    @Transactional
    public void deactivateNotification(Long id) {
        AdminNotification notification = getNotification(id);
        notification.setStatus(AdminNotificationStatus.INACTIVE);
    }

    /**
     * Удаляет уведомление из истории.
     */
    @Transactional
    public void deleteNotification(Long id) {
        notificationRepository.deleteById(id);
    }

    /**
     * Запрашивает повторный показ уведомления пользователям.
     */
    @Transactional
    public void requestReset(Long id) {
        AdminNotification notification = getNotification(id);
        notification.setResetRequested(true);
    }
}
