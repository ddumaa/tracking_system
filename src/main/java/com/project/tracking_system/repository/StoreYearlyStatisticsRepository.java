package com.project.tracking_system.repository;

import com.project.tracking_system.entity.StoreYearlyStatistics;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Repository for yearly store statistics.
 */
public interface StoreYearlyStatisticsRepository extends JpaRepository<StoreYearlyStatistics, Long> {

    Optional<StoreYearlyStatistics> findByStoreIdAndPeriodYearAndPeriodNumber(Long storeId, int periodYear, int periodNumber);
}
