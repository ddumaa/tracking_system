package com.project.tracking_system.repository;

import com.project.tracking_system.entity.StoreMonthlyStatistics;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Репозиторий для статистики магазинов по месяцам.
 */
public interface StoreMonthlyStatisticsRepository extends JpaRepository<StoreMonthlyStatistics, Long> {

    Optional<StoreMonthlyStatistics> findByStoreIdAndPeriodYearAndPeriodNumber(Long storeId, int periodYear, int periodNumber);

    /**
     * Найти статистику для нескольких магазинов за указанный месяц года.
     *
     * @param storeIds    список идентификаторов магазинов
     * @param periodYear  год месяца
     * @param periodNumber номер месяца (1-12)
     * @return список месячной статистики, по одному элементу на магазин
     */
    List<StoreMonthlyStatistics> findByStoreIdInAndPeriodYearAndPeriodNumber(List<Long> storeIds, int periodYear, int periodNumber);

    /**
     * Удалить месячную статистику конкретного магазина.
     */
    @Modifying
    @Transactional
    @Query("DELETE FROM StoreMonthlyStatistics s WHERE s.store.id = :storeId")
    void deleteByStoreId(@Param("storeId") Long storeId);

    /**
     * Удалить месячную статистику всех магазинов пользователя.
     */
    @Modifying
    @Transactional
    @Query("DELETE FROM StoreMonthlyStatistics s WHERE s.store.owner.id = :userId")
    void deleteByUserId(@Param("userId") Long userId);
}
