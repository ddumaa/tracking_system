package com.project.tracking_system.repository;

import com.project.tracking_system.entity.StoreMonthlyStatistics;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Repository for monthly store statistics.
 */
public interface StoreMonthlyStatisticsRepository extends JpaRepository<StoreMonthlyStatistics, Long> {

    Optional<StoreMonthlyStatistics> findByStoreIdAndPeriodYearAndPeriodNumber(Long storeId, int periodYear, int periodNumber);
}
