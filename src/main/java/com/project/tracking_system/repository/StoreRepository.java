package com.project.tracking_system.repository;

import com.project.tracking_system.entity.Store;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * @author Dmitriy Anisimov
 * @date 11.03.2025
 */
public interface StoreRepository extends JpaRepository<Store, Long> {

    List<Store> findByOwnerId(Long userId);

    int countByOwnerId(Long ownerId);

    boolean existsByIdAndOwnerId(Long storeId, Long ownerId);

    // Получает список всех ID магазинов пользователя
    @Query("SELECT s.id FROM Store s WHERE s.owner.id = :ownerId")
    List<Long> findStoreIdsByOwnerId(@Param("ownerId") Long ownerId);
}