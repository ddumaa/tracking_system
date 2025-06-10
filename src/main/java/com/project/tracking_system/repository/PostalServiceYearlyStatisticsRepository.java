package com.project.tracking_system.repository;

import com.project.tracking_system.entity.PostalServiceType;
import com.project.tracking_system.entity.PostalServiceYearlyStatistics;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Репозиторий для годовой статистики по почтовым службам.
 */
public interface PostalServiceYearlyStatisticsRepository extends JpaRepository<PostalServiceYearlyStatistics, Long> {

    /**
     * Найти статистику почтовой службы за конкретный год.
     *
     * @param storeId          идентификатор магазина
     * @param postalServiceType тип почтовой службы
     * @param periodYear       год статистики
     * @param periodNumber     номер периода (для года всегда 1)
     * @return статистика почтовой службы за год, если найдена
     */
    Optional<PostalServiceYearlyStatistics> findByStoreIdAndPostalServiceTypeAndPeriodYearAndPeriodNumber(Long storeId, PostalServiceType postalServiceType, int periodYear, int periodNumber);

    /**
     * Delete yearly statistics for a store.
     */
    @Modifying
    @Transactional
    @Query("DELETE FROM PostalServiceYearlyStatistics s WHERE s.store.id = :storeId")
    void deleteByStoreId(@Param("storeId") Long storeId);

    /**
     * Delete yearly statistics for all stores of a user.
     */
    @Modifying
    @Transactional
    @Query("DELETE FROM PostalServiceYearlyStatistics s WHERE s.store.owner.id = :userId")
    void deleteByUserId(@Param("userId") Long userId);
}
