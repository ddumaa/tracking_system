package com.project.tracking_system.repository;

import com.project.tracking_system.entity.PostalServiceDailyStatistics;
import com.project.tracking_system.entity.PostalServiceType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

/**
 * Repository for daily postal service statistics.
 */
public interface PostalServiceDailyStatisticsRepository extends JpaRepository<PostalServiceDailyStatistics, Long> {

    /**
     * Find statistics for a postal service of a store within a date range.
     *
     * @param storeId            store identifier
     * @param postalServiceType  type of the postal service
     * @param from               start date (inclusive)
     * @param to                 end date (inclusive)
     * @return list of daily statistics
     */
    List<PostalServiceDailyStatistics> findByStoreIdAndPostalServiceTypeAndDateBetween(Long storeId,
                                                                                       PostalServiceType postalServiceType,
                                                                                       LocalDate from,
                                                                                       LocalDate to);

    /**
     * Find statistics for multiple stores for a postal service within a date range.
     *
     * @param storeIds           store identifiers
     * @param postalServiceType  type of the postal service
     * @param from               start date (inclusive)
     * @param to                 end date (inclusive)
     * @return list of daily statistics
     */
    List<PostalServiceDailyStatistics> findByStoreIdInAndPostalServiceTypeAndDateBetween(List<Long> storeIds,
                                                                                        PostalServiceType postalServiceType,
                                                                                        LocalDate from,
                                                                                        LocalDate to);
}
