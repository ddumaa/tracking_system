package com.project.tracking_system.repository;

import com.project.tracking_system.entity.BuyerAnnouncementState;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Репозиторий состояния объявлений для покупателей в Telegram.
 */
public interface BuyerAnnouncementStateRepository extends JpaRepository<BuyerAnnouncementState, Long> {
}

