package com.project.tracking_system.repository;

import com.project.tracking_system.entity.BuyerAnnouncementState;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Репозиторий состояния объявлений для покупателей в Telegram.
 */
public interface BuyerAnnouncementStateRepository extends JpaRepository<BuyerAnnouncementState, Long> {

    /**
     * Возвращает состояния объявлений, связанные с указанным уведомлением администратора.
     *
     * @param notificationId идентификатор административного уведомления
     * @return список состояний пользователей, подписанных на указанное уведомление
     */
    List<BuyerAnnouncementState> findAllByCurrentNotificationId(Long notificationId);
}

