package com.project.tracking_system.repository;

import com.project.tracking_system.entity.PostalServiceType;
import com.project.tracking_system.entity.PostalServiceWeeklyStatistics;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Репозиторий для недельной статистики по почтовым службам.
 */
public interface PostalServiceWeeklyStatisticsRepository extends JpaRepository<PostalServiceWeeklyStatistics, Long> {

    /**
     * Найти статистику почтовой службы за конкретную неделю.
     *
     * @param storeId          идентификатор магазина
     * @param postalServiceType тип почтовой службы
     * @param periodYear       год недели
     * @param periodNumber     номер недели
     * @return статистика почтовой службы за неделю, если найдена
     */
    Optional<PostalServiceWeeklyStatistics> findByStoreIdAndPostalServiceTypeAndPeriodYearAndPeriodNumber(Long storeId, PostalServiceType postalServiceType, int periodYear, int periodNumber);
}
