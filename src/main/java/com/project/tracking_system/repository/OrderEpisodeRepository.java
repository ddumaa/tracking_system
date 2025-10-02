package com.project.tracking_system.repository;

import com.project.tracking_system.entity.OrderEpisode;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Репозиторий для работы с эпизодами заказов.
 */
public interface OrderEpisodeRepository extends JpaRepository<OrderEpisode, Long> {
}
