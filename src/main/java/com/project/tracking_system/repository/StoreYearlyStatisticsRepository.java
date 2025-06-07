package com.project.tracking_system.repository;

import com.project.tracking_system.entity.StoreYearlyStatistics;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for yearly store statistics.
 */
public interface StoreYearlyStatisticsRepository extends JpaRepository<StoreYearlyStatistics, Long> {

    Optional<StoreYearlyStatistics> findByStoreIdAndPeriodYearAndPeriodNumber(Long storeId, int periodYear, int periodNumber);

    /**
     * Find statistics for multiple stores for the given year.
     *
     * @param storeIds    list of store identifiers
     * @param periodYear  year of the statistics
     * @param periodNumber always 1 for yearly aggregation
     * @return list of yearly statistics, one per store if present
     */
    List<StoreYearlyStatistics> findByStoreIdInAndPeriodYearAndPeriodNumber(List<Long> storeIds, int periodYear, int periodNumber);
}
