package com.project.tracking_system.repository;

import com.project.tracking_system.entity.StoreStatistics;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * @author Dmitriy Anisimov
 * @date 11.03.2025
 */
public interface StoreAnalyticsRepository extends JpaRepository<StoreStatistics, Long> {

    /**
     * Найти статистику магазина по его идентификатору.
     *
     * @param storeId идентификатор магазина
     * @return статистика магазина, если найдена
     */
    Optional<StoreStatistics> findByStoreId(Long storeId);

    @Query("""
        SELECT s FROM StoreStatistics s
        WHERE s.store.owner.id = :userId
    """)
    List<StoreStatistics> findAllByUserId(@Param("userId") Long userId);

    /**
     * Получить статистику сразу по нескольким магазинам.
     */
    List<StoreStatistics> findByStoreIdIn(List<Long> storeIds);

    /**
     * Удалить статистику конкретного магазина.
     *
     * @param storeId идентификатор магазина
     */
    @Modifying
    @Transactional
    @Query("DELETE FROM StoreStatistics s WHERE s.store.id = :storeId")
    void deleteByStoreId(@Param("storeId") Long storeId);

    /**
     * Удалить всю статистику всех магазинов пользователя.
     *
     * @param userId идентификатор пользователя
     */
    @Modifying
    @Transactional
    @Query("DELETE FROM StoreStatistics s WHERE s.store.owner.id = :userId")
    void deleteByUserId(@Param("userId") Long userId);


}
