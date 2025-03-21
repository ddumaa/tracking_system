package com.project.tracking_system.repository;

import com.project.tracking_system.entity.DeliveryHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

/**
 * @author Dmitriy Anisimov
 * @date 15.03.2025
 */
public interface DeliveryHistoryRepository extends JpaRepository<DeliveryHistory, Integer> {

    Optional<DeliveryHistory> findByTrackParcelId(Long trackParcelId);

    @Query(value = """
    SELECT AVG(EXTRACT(EPOCH FROM (received_date - send_date)) / 86400) 
    FROM tb_delivery_history 
    WHERE store_id = :storeId
    AND received_date IS NOT NULL
    """, nativeQuery = true)
    Double findAverageDeliveryTimeForStore(@Param("storeId") Long storeId);



}