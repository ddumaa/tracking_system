package com.project.tracking_system.repository;

import com.project.tracking_system.entity.PostalServiceStatistics;
import com.project.tracking_system.entity.PostalServiceType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PostalServiceStatisticsRepository extends JpaRepository<PostalServiceStatistics, Long> {

    Optional<PostalServiceStatistics> findByStoreIdAndPostalServiceType(Long storeId, PostalServiceType postalServiceType);

}
