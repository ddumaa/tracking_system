package com.project.tracking_system.repository;

import com.project.tracking_system.entity.PostalServiceType;
import com.project.tracking_system.entity.PostalServiceWeeklyStatistics;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Repository for weekly postal service statistics.
 */
public interface PostalServiceWeeklyStatisticsRepository extends JpaRepository<PostalServiceWeeklyStatistics, Long> {

    Optional<PostalServiceWeeklyStatistics> findByStoreIdAndPostalServiceTypeAndPeriodYearAndPeriodNumber(Long storeId, PostalServiceType postalServiceType, int periodYear, int periodNumber);
}
