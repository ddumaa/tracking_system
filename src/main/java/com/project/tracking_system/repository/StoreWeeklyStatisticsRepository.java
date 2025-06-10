package com.project.tracking_system.repository;

import com.project.tracking_system.entity.StoreWeeklyStatistics;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Репозиторий для статистики магазинов по неделям.
 */
public interface StoreWeeklyStatisticsRepository extends JpaRepository<StoreWeeklyStatistics, Long> {

    Optional<StoreWeeklyStatistics> findByStoreIdAndPeriodYearAndPeriodNumber(Long storeId, int periodYear, int periodNumber);

    /**
     * Найти статистику для нескольких магазинов за указанную неделю года.
     *
     * @param storeIds    список идентификаторов магазинов
     * @param periodYear  год недели
     * @param periodNumber номер недели в году
     * @return список недельной статистики, по одному элементу на магазин
     */
    List<StoreWeeklyStatistics> findByStoreIdInAndPeriodYearAndPeriodNumber(List<Long> storeIds, int periodYear, int periodNumber);

    /**
     * Удалить недельную статистику конкретного магазина.
     */
    @Modifying
    @Transactional
    @Query("DELETE FROM StoreWeeklyStatistics s WHERE s.store.id = :storeId")
    void deleteByStoreId(@Param("storeId") Long storeId);

    /**
     * Удалить недельную статистику всех магазинов пользователя.
     */
    @Modifying
    @Transactional
    @Query("DELETE FROM StoreWeeklyStatistics s WHERE s.store.owner.id = :userId")
    void deleteByUserId(@Param("userId") Long userId);
}
