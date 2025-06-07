package com.project.tracking_system.repository;

import com.project.tracking_system.entity.StoreWeeklyStatistics;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for weekly store statistics.
 */
public interface StoreWeeklyStatisticsRepository extends JpaRepository<StoreWeeklyStatistics, Long> {

    Optional<StoreWeeklyStatistics> findByStoreIdAndPeriodYearAndPeriodNumber(Long storeId, int periodYear, int periodNumber);

    /**
     * Find statistics for multiple stores for the given week of a year.
     *
     * @param storeIds    list of store identifiers
     * @param periodYear  year of the week
     * @param periodNumber number of the week within the year
     * @return list of weekly statistics, one per store if present
     */
    List<StoreWeeklyStatistics> findByStoreIdInAndPeriodYearAndPeriodNumber(List<Long> storeIds, int periodYear, int periodNumber);
}
