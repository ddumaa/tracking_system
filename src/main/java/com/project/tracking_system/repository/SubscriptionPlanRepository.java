package com.project.tracking_system.repository;

import com.project.tracking_system.entity.SubscriptionCode;
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

    Optional<SubscriptionPlan> findById(Long id);

    Optional<SubscriptionPlan> findByCode(SubscriptionCode code);

}
