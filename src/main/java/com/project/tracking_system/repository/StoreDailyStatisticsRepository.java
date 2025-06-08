package com.project.tracking_system.repository;

import com.project.tracking_system.entity.StoreDailyStatistics;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Репозиторий для ежедневной статистики магазинов.
 */
public interface StoreDailyStatisticsRepository extends JpaRepository<StoreDailyStatistics, Long> {

    /**
     * Найти статистику для магазина на конкретную дату.
     *
     * @param storeId идентификатор магазина
     * @param date    дата статистики
     * @return ежедневная статистика, если найдена
     */
    Optional<StoreDailyStatistics> findByStoreIdAndDate(Long storeId, LocalDate date);

    /**
     * Найти статистику для одного магазина за диапазон дат.
     *
     * @param storeId идентификатор магазина
     * @param from    дата начала (включительно)
     * @param to      дата окончания (включительно)
     * @return список ежедневной статистики
     */
    List<StoreDailyStatistics> findByStoreIdAndDateBetween(Long storeId, LocalDate from, LocalDate to);

    /**
     * Найти статистику для нескольких магазинов за диапазон дат.
     *
     * @param storeIds список идентификаторов магазинов
     * @param from     дата начала (включительно)
     * @param to       дата окончания (включительно)
     * @return список ежедневной статистики
     */
    List<StoreDailyStatistics> findByStoreIdInAndDateBetween(List<Long> storeIds, LocalDate from, LocalDate to);

    /**
     * Найти статистику для всех магазинов на конкретную дату.
     *
     * @param date дата статистики
     * @return список ежедневной статистики
     */
    List<StoreDailyStatistics> findByDate(LocalDate date);
}
