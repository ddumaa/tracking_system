package com.project.tracking_system.repository;

import com.project.tracking_system.entity.PostalServiceStatistics;
import com.project.tracking_system.entity.PostalServiceType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PostalServiceStatisticsRepository extends JpaRepository<PostalServiceStatistics, Long> {

    /**
     * Получить статистику конкретной почтовой службы магазина.
     *
     * @param storeId идентификатор магазина
     * @param postalServiceType тип почтовой службы
     * @return статистика почтовой службы, если найдена
     */
    Optional<PostalServiceStatistics> findByStoreIdAndPostalServiceType(Long storeId, PostalServiceType postalServiceType);

    /**
     * Получить статистику всех почтовых служб для магазина.
     */
    List<PostalServiceStatistics> findByStoreId(Long storeId);

    /**
     * Получить статистику для нескольких магазинов.
     */
    List<PostalServiceStatistics> findByStoreIdIn(List<Long> storeIds);

}
