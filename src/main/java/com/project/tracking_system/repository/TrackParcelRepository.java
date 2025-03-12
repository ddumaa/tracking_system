package com.project.tracking_system.repository;

import com.project.tracking_system.entity.TrackParcel;
import com.project.tracking_system.model.GlobalStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface TrackParcelRepository extends JpaRepository<TrackParcel, Long> {

    List<TrackParcel> findByUserId(Long userId);

    TrackParcel findByNumberAndUserId(String number, Long userId);

    List<TrackParcel> findByNumberInAndUserId(List<String> numbers, Long userId);

    TrackParcel findByNumberAndStoreIdAndUserId(String number, Long storeId, Long userId);

    Page<TrackParcel> findByUserId(Long userId, Pageable pageable);

    // Поиск всех посылок по магазину
    List<TrackParcel> findByStoreId(Long storeId);

    boolean existsByNumberAndUserId(String number, Long userId);

    // Поиск посылок по списку магазинов с пагинацией.
    Page<TrackParcel> findByStoreIdIn(List<Long> storeIds, Pageable pageable);

    // Поиск одной посылки по номеру в рамках конкретного магазина
    TrackParcel findByNumberAndStoreId(String number, Long storeId);

    // Поиск посылок по статусу для конкретного магазина (с пагинацией)
    @Query("SELECT t FROM TrackParcel t WHERE t.store.id IN :storeIds AND t.status = :status")
    Page<TrackParcel> findByStoreIdInAndStatus(@Param("storeIds") List<Long> storeIds, @Param("status") GlobalStatus status, Pageable pageable);

    // Подсчёт всех посылок в магазине
    @Query("SELECT COUNT(t) FROM TrackParcel t WHERE t.store.id = :storeId")
    int countByStoreId(@Param("storeId") Long storeId);

    // Подсчёт всех посылок пользователя
    @Query("SELECT COUNT(t) FROM TrackParcel t WHERE t.user.id = :userId")
    int countByUserId(@Param("userId") Long userId);

    // Подсчёт количества доставленных посылок
    @Query("SELECT COUNT(p) FROM TrackParcel p WHERE p.store.id = :storeId AND p.status = :status")
    int countByStoreIdAndStatus(@Param("storeId") Long storeId, @Param("status") GlobalStatus status);

    // Подсчёт среднего срока доставки (в днях)
    @Query(value = """
        SELECT AVG(EXTRACT(EPOCH FROM (received_date - send_date)) / 86400) 
        FROM tb_track_parcels 
        WHERE store_id = :storeId AND received_date IS NOT NULL
        """, nativeQuery = true)
    Double findAverageDeliveryTimeForStore(@Param("storeId") Long storeId);


    @Modifying
    @Transactional
    @Query("DELETE FROM TrackParcel t WHERE t.store.id = :storeId")
    void deleteByStoreId(@Param("storeId") Long storeId);
}