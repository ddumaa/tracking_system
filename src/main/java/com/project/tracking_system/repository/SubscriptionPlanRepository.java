package com.project.tracking_system.repository;

import com.project.tracking_system.entity.SubscriptionPlan;
import org.springframework.data.jpa.repository.JpaRepository;
import java.math.BigDecimal;

import java.util.Optional;

/**
 * @author Dmitriy Anisimov
 * @date 13.02.2025
 */

public interface SubscriptionPlanRepository extends JpaRepository<SubscriptionPlan, Long> {

    Optional<SubscriptionPlan> findByCode(String code);

    /**
     * Найти первый план с указанной ценой.
     *
     * @param price стоимость плана
     * @return найденный план или {@code Optional.empty()}
     */
    Optional<SubscriptionPlan> findFirstByPrice(BigDecimal price);

    /**
     * Найти первый план с ценой выше указанной.
     *
     * @param price минимальная цена плана
     * @return найденный платный план или {@code Optional.empty()}
     */
    Optional<SubscriptionPlan> findFirstByPriceGreaterThan(BigDecimal price);

}
