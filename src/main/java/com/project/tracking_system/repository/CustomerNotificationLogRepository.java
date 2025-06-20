package com.project.tracking_system.repository;

import com.project.tracking_system.entity.CustomerNotificationLog;
import com.project.tracking_system.entity.GlobalStatus;
import com.project.tracking_system.entity.NotificationType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Репозиторий для работы с логом уведомлений покупателей.
 */
public interface CustomerNotificationLogRepository extends JpaRepository<CustomerNotificationLog, Long> {

    /**
     * Проверить, существует ли запись уведомления с заданными параметрами.
     *
     * @param parcelId идентификатор посылки
     * @param status   статус посылки
     * @param type     тип уведомления
     * @return {@code true}, если запись существует
     */
    boolean existsByParcelIdAndStatusAndNotificationType(Long parcelId,
                                                         GlobalStatus status,
                                                         NotificationType type);

    /**
     * Найти последнее напоминание для посылки.
     */
    CustomerNotificationLog findTopByParcelIdAndNotificationTypeOrderBySentAtDesc(Long parcelId,
                                           NotificationType type);

    /**
     * Получить последние десять записей журнала уведомлений.
     *
     * @return список из десяти последних уведомлений
     */
    List<CustomerNotificationLog> findTop10ByOrderBySentAtDesc();

    /**
     * Удалить все записи лога конкретного покупателя.
     *
     * @param customerId идентификатор покупателя
     */
    @Modifying
    @Transactional
    void deleteByCustomerId(Long customerId);
}
