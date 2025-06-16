package com.project.tracking_system.repository;

import com.project.tracking_system.entity.CustomerNotificationLog;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Репозиторий для работы с логом уведомлений покупателей.
 */
public interface CustomerNotificationLogRepository extends JpaRepository<CustomerNotificationLog, Long> {
}
