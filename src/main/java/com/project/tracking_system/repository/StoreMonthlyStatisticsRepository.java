package com.project.tracking_system.repository;

import com.project.tracking_system.entity.StoreMonthlyStatistics;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

/**
 * Репозиторий для статистики магазинов по месяцам.
 */
public interface StoreMonthlyStatisticsRepository
        extends JpaRepository<StoreMonthlyStatistics, Long>,
        DeletableByStoreOrUser<StoreMonthlyStatistics, Long> {

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
    // Методы удаления определены в DeletableByStoreOrUser
}
