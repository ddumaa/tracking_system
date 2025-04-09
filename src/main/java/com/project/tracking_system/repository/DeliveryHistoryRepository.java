package com.project.tracking_system.repository;

import com.project.tracking_system.entity.DeliveryHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;

/**
 * @author Dmitriy Anisimov
 * @date 15.03.2025
 */
public interface DeliveryHistoryRepository extends JpaRepository<DeliveryHistory, Integer> {

    Optional<DeliveryHistory> findByTrackParcelId(Long trackParcelId);

    @Query(value = """
    SELECT AVG(EXTRACT(EPOCH FROM (arrived_date - send_date)) / 86400)
    FROM tb_delivery_history
    WHERE store_id = :storeId
    AND arrived_date IS NOT NULL
    AND send_date IS NOT NULL
    """, nativeQuery = true)
    Double findAverageDeliveryTimeToFinalPoint(@Param("storeId") Long storeId);


    @Query(value = """
    SELECT 
        postal_service AS postalService,
        COUNT(*) AS sent,
        SUM(CASE WHEN arrived_date IS NOT NULL THEN 1 ELSE 0 END) AS delivered,
        SUM(CASE WHEN returned_date IS NOT NULL THEN 1 ELSE 0 END) AS returned,
        AVG(EXTRACT(EPOCH FROM (arrived_date - send_date)) / 86400) AS avgDeliveryDays,
        AVG(EXTRACT(EPOCH FROM (received_date - arrived_date)) / 86400) AS avgPickupTimeDays
    FROM tb_delivery_history
    WHERE store_id = :storeId
    GROUP BY postal_service
    """, nativeQuery = true)
    List<Object[]> getRawStatsByPostalService(@Param("storeId") Long storeId);


    @Query(value = """
    SELECT 
        postal_service AS postalService,
        COUNT(*) AS sent,
        SUM(CASE WHEN arrived_date IS NOT NULL THEN 1 ELSE 0 END) AS delivered,
        SUM(CASE WHEN returned_date IS NOT NULL THEN 1 ELSE 0 END) AS returned,
        AVG(EXTRACT(EPOCH FROM (arrived_date - send_date)) / 86400) AS avgDeliveryDays,
        AVG(EXTRACT(EPOCH FROM (received_date - arrived_date)) / 86400) AS avgPickupTimeDays
    FROM tb_delivery_history
    WHERE store_id IN (:storeIds)
    GROUP BY postal_service
    """, nativeQuery = true)
    List<Object[]> getRawStatsByPostalServiceForStores(@Param("storeIds") List<Long> storeIds);


    @Query(value = """
    SELECT 
        DATE_TRUNC(:interval, send_date) AS period,
        COUNT(*) AS sent,
        SUM(CASE WHEN received_date IS NOT NULL THEN 1 ELSE 0 END) AS delivered,
        SUM(CASE WHEN returned_date IS NOT NULL THEN 1 ELSE 0 END) AS returned
    FROM tb_delivery_history
    WHERE store_id IN (:storeIds)
      AND send_date IS NOT NULL
      AND send_date BETWEEN :from AND :to
    GROUP BY period
    ORDER BY period
    """, nativeQuery = true)
    List<Object[]> getSentDeliveredReturnedGroupedByPeriod(
            @Param("storeIds") List<Long> storeIds,
            @Param("interval") String interval,
            @Param("from") Timestamp from,
            @Param("to") Timestamp to
    );

    @Query(value = """
    SELECT AVG(EXTRACT(EPOCH FROM (received_date - arrived_date)) / 86400)
    FROM tb_delivery_history
    WHERE store_id = :storeId
    AND received_date IS NOT NULL
    AND arrived_date IS NOT NULL
    """, nativeQuery = true)
    Double findAvgPickupTimeForStore(@Param("storeId") Long storeId);

}