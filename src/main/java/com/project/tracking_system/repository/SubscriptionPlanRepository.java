package com.project.tracking_system.repository;

import com.project.tracking_system.entity.SubscriptionPlan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

/**
 * @author Dmitriy Anisimov
 * @date 13.02.2025
 */

public interface SubscriptionPlanRepository extends JpaRepository<SubscriptionPlan, Long> {

    Optional<SubscriptionPlan> findByName(String name);

    Optional<SubscriptionPlan> findById(Long id);

    @Query("SELECT sp.maxTrackUpdates FROM SubscriptionPlan sp WHERE sp.name = :planName")
    Integer getMaxUpdatesByName(@Param("planName") String planName);

    @Query("SELECT sp.maxSavedTracks FROM SubscriptionPlan sp WHERE sp.name = :planName")
    Integer getMaxSavedTracksByName(@Param("planName") String planName);

    @Query("SELECT sp.maxTracksPerFile FROM SubscriptionPlan sp WHERE sp.name = :planName")
    Integer getMaxTracksPerFileByName(@Param("planName") String planName);

}
