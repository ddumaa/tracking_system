package com.project.tracking_system.repository;

import com.project.tracking_system.entity.AdminNotification;
import com.project.tracking_system.entity.AdminNotificationStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * Репозиторий управления административными уведомлениями.
 */
public interface AdminNotificationRepository extends JpaRepository<AdminNotification, Long> {

    /**
     * Находит активное уведомление, если оно существует.
     */
    @EntityGraph(attributePaths = "bodyLines")
    Optional<AdminNotification> findFirstByStatus(AdminNotificationStatus status);

    /**
     * Возвращает историю уведомлений в порядке от новых к старым.
     */
    List<AdminNotification> findAllByOrderByCreatedAtDesc();
}
