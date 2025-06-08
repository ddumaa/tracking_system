package com.project.tracking_system.repository;

import com.project.tracking_system.entity.StoreWeeklyStatistics;
import org.springframework.data.jpa.repository.JpaRepository;

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
}
