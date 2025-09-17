package com.project.tracking_system.repository;

import com.project.tracking_system.entity.BuyerBotScreenState;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Репозиторий хранения якорных сообщений покупателей в Telegram.
 */
public interface BuyerBotScreenStateRepository extends JpaRepository<BuyerBotScreenState, Long> {
}

