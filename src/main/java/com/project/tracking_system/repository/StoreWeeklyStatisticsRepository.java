package com.project.tracking_system.repository;

import com.project.tracking_system.entity.StoreWeeklyStatistics;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Repository for weekly store statistics.
 */
public interface StoreWeeklyStatisticsRepository extends JpaRepository<StoreWeeklyStatistics, Long> {

    Optional<StoreWeeklyStatistics> findByStoreIdAndPeriodYearAndPeriodNumber(Long storeId, int periodYear, int periodNumber);
}
