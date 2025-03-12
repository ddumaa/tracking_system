package com.project.tracking_system.repository;

import com.project.tracking_system.entity.StoreStatistics;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

/**
 * @author Dmitriy Anisimov
 * @date 11.03.2025
 */
public interface AnalyticsRepository extends JpaRepository<StoreStatistics, Long> {

    Optional<StoreStatistics> findByStoreId(Long storeId);

    @Query("SELECT s.id FROM Store s")
    List<Long> findAllStoreIds();

}