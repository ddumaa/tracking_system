package com.project.tracking_system.repository;

import com.project.tracking_system.entity.PostalServiceType;
import com.project.tracking_system.entity.PostalServiceYearlyStatistics;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Repository for yearly postal service statistics.
 */
public interface PostalServiceYearlyStatisticsRepository extends JpaRepository<PostalServiceYearlyStatistics, Long> {

    Optional<PostalServiceYearlyStatistics> findByStoreIdAndPostalServiceTypeAndPeriodYearAndPeriodNumber(Long storeId, PostalServiceType postalServiceType, int periodYear, int periodNumber);
}
