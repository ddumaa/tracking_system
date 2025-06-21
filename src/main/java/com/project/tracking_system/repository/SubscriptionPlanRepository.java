package com.project.tracking_system.repository;

import com.project.tracking_system.entity.SubscriptionPlan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.math.BigDecimal;

import java.util.List;
import java.util.Optional;

/**
 * @author Dmitriy Anisimov
 * @date 13.02.2025
 */

public interface SubscriptionPlanRepository extends JpaRepository<SubscriptionPlan, Long> {

    @Query("SELECT sp FROM SubscriptionPlan sp LEFT JOIN FETCH sp.features WHERE sp.code = :code")
    Optional<SubscriptionPlan> findByCode(@Param("code") String code);

    /**
     * Найти первый бесплатный тарифный план.
     *
     * @param monthly стоимость в месяц
     * @param annual  стоимость в год
     * @return найденный план или {@code Optional.empty()}
     */
    Optional<SubscriptionPlan> findFirstByMonthlyPriceAndAnnualPrice(BigDecimal monthly,
                                                                    BigDecimal annual);

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
     * Найти первый платный тарифный план.
     *
     * @param monthly минимальная месячная цена
     * @param annual  минимальная годовая цена
     * @return найденный платный план или {@code Optional.empty()}
     */
    Optional<SubscriptionPlan> findFirstByMonthlyPriceGreaterThanOrAnnualPriceGreaterThan(BigDecimal monthly,
                                                                                        BigDecimal annual);

}
