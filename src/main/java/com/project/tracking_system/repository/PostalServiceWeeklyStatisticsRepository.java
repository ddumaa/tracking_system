package com.project.tracking_system.repository;

import com.project.tracking_system.entity.PostalServiceType;
import com.project.tracking_system.entity.PostalServiceWeeklyStatistics;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

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

    /**
     * Delete weekly statistics for a store.
     */
    @Modifying
    @Transactional
    @Query("DELETE FROM PostalServiceWeeklyStatistics s WHERE s.store.id = :storeId")
    void deleteByStoreId(@Param("storeId") Long storeId);

    /**
     * Delete weekly statistics for all stores of a user.
     */
    @Modifying
    @Transactional
    @Query("DELETE FROM PostalServiceWeeklyStatistics s WHERE s.store.owner.id = :userId")
    void deleteByUserId(@Param("userId") Long userId);
}
