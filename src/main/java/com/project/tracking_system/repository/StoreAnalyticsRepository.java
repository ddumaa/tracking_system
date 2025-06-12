package com.project.tracking_system.repository;

import com.project.tracking_system.entity.StoreStatistics;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

// Интерфейс для удалений
import com.project.tracking_system.repository.DeletableByStoreOrUser;

import java.util.List;
import java.util.Optional;

/**
 * @author Dmitriy Anisimov
 * @date 11.03.2025
 */
public interface StoreAnalyticsRepository
        extends JpaRepository<StoreStatistics, Long>,
        DeletableByStoreOrUser<StoreStatistics, Long> {

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
     * Атомарно увеличивает счётчик отправленных посылок магазина.
     *
     * @param storeId идентификатор магазина
     * @param delta   величина увеличения
     * @return количество обновлённых записей
     */
    @Modifying
    @Transactional
    @Query("""
        UPDATE StoreStatistics s
        SET s.totalSent = s.totalSent + :delta,
            s.updatedAt = CURRENT_TIMESTAMP
        WHERE s.store.id = :storeId
        """)
    int incrementTotalSent(@Param("storeId") Long storeId, @Param("delta") int delta);


    /**
     * Обнулить счётчики для всех магазинов пользователя.
     *
     * @param userId идентификатор пользователя
     */
    @Modifying
    @Transactional
    @Query("""
        UPDATE StoreStatistics s
        SET s.totalSent = 0,
            s.totalDelivered = 0,
            s.totalReturned = 0,
            s.sumDeliveryDays = 0,
            s.sumPickupDays = 0,
            s.updatedAt = NULL
        WHERE s.store.owner.id = :userId
        """)
    void resetByUserId(@Param("userId") Long userId);

    /**
     * Обнулить счётчики конкретного магазина.
     *
     * @param storeId идентификатор магазина
     */
    @Modifying
    @Transactional
    @Query("""
        UPDATE StoreStatistics s
        SET s.totalSent = 0,
            s.totalDelivered = 0,
            s.totalReturned = 0,
            s.sumDeliveryDays = 0,
            s.sumPickupDays = 0,
            s.updatedAt = NULL
        WHERE s.store.id = :storeId
        """)
    void resetByStoreId(@Param("storeId") Long storeId);


}
