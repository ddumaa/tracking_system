package com.project.tracking_system.repository;

import com.project.tracking_system.entity.PostalServiceMonthlyStatistics;
import com.project.tracking_system.entity.PostalServiceType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Repository for monthly postal service statistics.
 */
public interface PostalServiceMonthlyStatisticsRepository extends JpaRepository<PostalServiceMonthlyStatistics, Long> {

    Optional<PostalServiceMonthlyStatistics> findByStoreIdAndPostalServiceTypeAndPeriodYearAndPeriodNumber(Long storeId, PostalServiceType postalServiceType, int periodYear, int periodNumber);
}
