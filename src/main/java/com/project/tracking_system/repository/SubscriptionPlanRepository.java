package com.project.tracking_system.repository;

import com.project.tracking_system.entity.SubscriptionPlan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.math.BigDecimal;

import java.util.Optional;

/**
 * @author Dmitriy Anisimov
 * @date 13.02.2025
 */

public interface SubscriptionPlanRepository extends JpaRepository<SubscriptionPlan, Long> {

    @Query("SELECT sp FROM SubscriptionPlan sp LEFT JOIN FETCH sp.features WHERE sp.code = :code")
    Optional<SubscriptionPlan> findByCode(@Param("code") String code);

    /**
     * Найти первый план с указанной ценой.
     *
     * @param price стоимость плана
     * @return найденный план или {@code Optional.empty()}
     */
    Optional<SubscriptionPlan> findFirstByPrice(BigDecimal price);

    /**
     * Получить все тарифы, отсортированные по позиции.
     *
     * @return список тарифных планов
     */
    List<SubscriptionPlan> findAllByOrderByPositionAsc();

    /**
     * Найти максимальную позицию среди тарифных планов.
     *
     * @return максимальная позиция или {@code Optional.empty()}
     */
    @Query("SELECT MAX(sp.position) FROM SubscriptionPlan sp")
    Optional<Integer> findMaxPosition();

    /**
     * Найти первый план с ценой выше указанной.
     *
     * @param price минимальная цена плана
     * @return найденный платный план или {@code Optional.empty()}
     */
    Optional<SubscriptionPlan> findFirstByPriceGreaterThan(BigDecimal price);

}
