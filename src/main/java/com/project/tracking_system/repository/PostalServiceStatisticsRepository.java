package com.project.tracking_system.repository;

import com.project.tracking_system.entity.PostalServiceStatistics;
import com.project.tracking_system.entity.PostalServiceType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

// Интерфейс для удаления записей
import com.project.tracking_system.repository.DeletableByStoreOrUser;

import java.util.List;
import java.util.Optional;

public interface PostalServiceStatisticsRepository
        extends JpaRepository<PostalServiceStatistics, Long>,
        DeletableByStoreOrUser<PostalServiceStatistics, Long> {

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


    /**
     * Обнулить счётчики для всех служб доставки магазинов пользователя.
     *
     * @param userId идентификатор пользователя
     */
    @Modifying
    @Transactional
    @Query("""
        UPDATE PostalServiceStatistics p
        SET p.totalSent = 0,
            p.totalDelivered = 0,
            p.totalReturned = 0,
            p.sumDeliveryDays = 0,
            p.sumPickupDays = 0,
            p.updatedAt = NULL
        WHERE p.store.owner.id = :userId
        """)
    void resetByUserId(@Param("userId") Long userId);

    /**
     * Обнулить счётчики служб доставки конкретного магазина.
     *
     * @param storeId идентификатор магазина
     */
    @Modifying
    @Transactional
    @Query("""
        UPDATE PostalServiceStatistics p
        SET p.totalSent = 0,
            p.totalDelivered = 0,
            p.totalReturned = 0,
            p.sumDeliveryDays = 0,
            p.sumPickupDays = 0,
            p.updatedAt = NULL
        WHERE p.store.id = :storeId
        """)
    void resetByStoreId(@Param("storeId") Long storeId);

}
