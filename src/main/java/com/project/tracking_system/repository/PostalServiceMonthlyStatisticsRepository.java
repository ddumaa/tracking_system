package com.project.tracking_system.repository;

import com.project.tracking_system.entity.PostalServiceMonthlyStatistics;
import com.project.tracking_system.entity.PostalServiceType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Репозиторий для месячной статистики по почтовым службам.
 */
public interface PostalServiceMonthlyStatisticsRepository extends JpaRepository<PostalServiceMonthlyStatistics, Long> {

    /**
     * Найти статистику почтовой службы за конкретный месяц.
     *
     * @param storeId          идентификатор магазина
     * @param postalServiceType тип почтовой службы
     * @param periodYear       год статистики
     * @param periodNumber     номер месяца
     * @return статистика почтовой службы за месяц, если найдена
     */
    Optional<PostalServiceMonthlyStatistics> findByStoreIdAndPostalServiceTypeAndPeriodYearAndPeriodNumber(Long storeId, PostalServiceType postalServiceType, int periodYear, int periodNumber);

    /**
     * Delete monthly statistics for a store.
     */
    @Modifying
    @Transactional
    @Query("DELETE FROM PostalServiceMonthlyStatistics s WHERE s.store.id = :storeId")
    void deleteByStoreId(@Param("storeId") Long storeId);

    /**
     * Delete monthly statistics for all stores of a user.
     */
    @Modifying
    @Transactional
    @Query("DELETE FROM PostalServiceMonthlyStatistics s WHERE s.store.owner.id = :userId")
    void deleteByUserId(@Param("userId") Long userId);
}
