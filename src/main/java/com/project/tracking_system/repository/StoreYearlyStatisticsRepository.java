package com.project.tracking_system.repository;

import com.project.tracking_system.entity.StoreYearlyStatistics;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Репозиторий для статистики магазинов по годам.
 */
public interface StoreYearlyStatisticsRepository extends JpaRepository<StoreYearlyStatistics, Long> {

    Optional<StoreYearlyStatistics> findByStoreIdAndPeriodYearAndPeriodNumber(Long storeId, int periodYear, int periodNumber);

    /**
     * Найти статистику для нескольких магазинов за указанный год.
     *
     * @param storeIds    список идентификаторов магазинов
     * @param periodYear  год статистики
     * @param periodNumber всегда 1 для годовой агрегации
     * @return список годовой статистики, по одному элементу на магазин
     */
    List<StoreYearlyStatistics> findByStoreIdInAndPeriodYearAndPeriodNumber(List<Long> storeIds, int periodYear, int periodNumber);

    /**
     * Удалить годовую статистику конкретного магазина.
     */
    @Modifying
    @Transactional
    @Query("DELETE FROM StoreYearlyStatistics s WHERE s.store.id = :storeId")
    void deleteByStoreId(@Param("storeId") Long storeId);

    /**
     * Удалить годовую статистику всех магазинов пользователя.
     */
    @Modifying
    @Transactional
    @Query("DELETE FROM StoreYearlyStatistics s WHERE s.store.owner.id = :userId")
    void deleteByUserId(@Param("userId") Long userId);
}
