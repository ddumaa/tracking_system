package com.project.tracking_system.repository;

import com.project.tracking_system.entity.DeliveryHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * @author Dmitriy Anisimov
 * @date 15.03.2025
 */
public interface DeliveryHistoryRepository extends JpaRepository<DeliveryHistory, Integer> {

    Optional<DeliveryHistory> findByTrackParcelId(Long trackParcelId);
}
