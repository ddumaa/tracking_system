package com.project.tracking_system.repository;

import com.project.tracking_system.entity.StoreStatistics;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * @author Dmitriy Anisimov
 * @date 11.03.2025
 */
public interface StoreAnalyticsRepository extends JpaRepository<StoreStatistics, Long> {

    Optional<StoreStatistics> findByStoreId(Long storeId);

    @Query("""
        SELECT s FROM StoreStatistics s
        WHERE s.store.owner.id = :userId
    """)
    List<StoreStatistics> findAllByUserId(@Param("userId") Long userId);

    /**
     * Fetch statistics for multiple stores at once.
     */
    List<StoreStatistics> findByStoreIdIn(List<Long> storeIds);


}
