package com.project.tracking_system.repository;

import com.project.tracking_system.entity.StoreMonthlyStatistics;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for monthly store statistics.
 */
public interface StoreMonthlyStatisticsRepository extends JpaRepository<StoreMonthlyStatistics, Long> {

    Optional<StoreMonthlyStatistics> findByStoreIdAndPeriodYearAndPeriodNumber(Long storeId, int periodYear, int periodNumber);

    /**
     * Find statistics for multiple stores for the given month of a year.
     *
     * @param storeIds    list of store identifiers
     * @param periodYear  year of the month
     * @param periodNumber number of the month (1-12)
     * @return list of monthly statistics, one per store if present
     */
    List<StoreMonthlyStatistics> findByStoreIdInAndPeriodYearAndPeriodNumber(List<Long> storeIds, int periodYear, int periodNumber);
}
