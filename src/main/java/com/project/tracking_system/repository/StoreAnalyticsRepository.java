package com.project.tracking_system.repository;

import com.project.tracking_system.entity.StoreStatistics;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;


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
     * Атомарно увеличивает счётчик доставленных посылок магазина и суммируемые значения.
     *
     * @param storeId      идентификатор магазина
     * @param delta        величина увеличения доставленных посылок
     * @param deliveryDays добавляемые дни доставки
     * @param pickupDays   добавляемые дни получения
     * @return количество обновлённых записей
     */
    @Modifying
    @Transactional
    @Query("""
        UPDATE StoreStatistics s
        SET s.totalDelivered = s.totalDelivered + :delta,
            s.sumDeliveryDays = s.sumDeliveryDays + :deliveryDays,
            s.sumPickupDays = s.sumPickupDays + :pickupDays,
            s.updatedAt = CURRENT_TIMESTAMP
        WHERE s.store.id = :storeId
        """)
    int incrementDelivered(@Param("storeId") Long storeId,
                           @Param("delta") int delta,
                           @Param("deliveryDays") java.math.BigDecimal deliveryDays,
                           @Param("pickupDays") java.math.BigDecimal pickupDays);

    /**
     * Атомарно увеличивает счётчик возвращённых посылок магазина и связанные суммы.
     *
     * @param storeId      идентификатор магазина
     * @param delta        величина увеличения возвращённых посылок
     * @param deliveryDays добавляемые дни доставки
     * @param pickupDays   добавляемые дни нахождения на пункте
     * @return количество обновлённых записей
     */
    @Modifying
    @Transactional
    @Query("""
        UPDATE StoreStatistics s
        SET s.totalReturned = s.totalReturned + :delta,
            s.sumDeliveryDays = s.sumDeliveryDays + :deliveryDays,
            s.sumPickupDays = s.sumPickupDays + :pickupDays,
            s.updatedAt = CURRENT_TIMESTAMP
        WHERE s.store.id = :storeId
        """)
    int incrementReturned(@Param("storeId") Long storeId,
                          @Param("delta") int delta,
                          @Param("deliveryDays") java.math.BigDecimal deliveryDays,
                          @Param("pickupDays") java.math.BigDecimal pickupDays);


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
