package com.project.tracking_system.repository;

import com.project.tracking_system.entity.StoreDailyStatistics;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Repository for daily statistics of stores.
 */
public interface StoreDailyStatisticsRepository extends JpaRepository<StoreDailyStatistics, Long> {

    /**
     * Find statistics for a store on a particular date.
     *
     * @param storeId store identifier
     * @param date    date of statistics
     * @return optional daily statistics
     */
    Optional<StoreDailyStatistics> findByStoreIdAndDate(Long storeId, LocalDate date);

    /**
     * Find statistics for a single store within a date range.
     *
     * @param storeId identifier of the store
     * @param from    start date (inclusive)
     * @param to      end date (inclusive)
     * @return list of daily statistics
     */
    List<StoreDailyStatistics> findByStoreIdAndDateBetween(Long storeId, LocalDate from, LocalDate to);

    /**
     * Find statistics for multiple stores within a date range.
     *
     * @param storeIds list of store identifiers
     * @param from     start date (inclusive)
     * @param to       end date (inclusive)
     * @return list of daily statistics
     */
    List<StoreDailyStatistics> findByStoreIdInAndDateBetween(List<Long> storeIds, LocalDate from, LocalDate to);

    /**
     * Find statistics for all stores on a specific date.
     *
     * @param date date of statistics
     * @return list of daily statistics
     */
    List<StoreDailyStatistics> findByDate(LocalDate date);
}
