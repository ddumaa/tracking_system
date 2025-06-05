package com.project.tracking_system.repository;

import com.project.tracking_system.entity.Store;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * @author Dmitriy Anisimov
 * @date 11.03.2025
 */
public interface StoreRepository extends JpaRepository<Store, Long> {


    /**
     * Получить магазин по его идентификатору.
     *
     * @param storeId идентификатор магазина
     * @return найденный магазин или {@code null}, если магазин не найден
     */
    Store findStoreById(Long storeId);

    /**
     * Возвращает единственный магазин пользователя, если он есть.
     */
    Optional<Store> findFirstByOwnerId(Long userId);

    /**
     * Возвращает список магазинов пользователя.
     */
    List<Store> findByOwnerId(Long userId);

    /**
     * Подсчёт количества магазинов у пользователя.
     *
     * @param ownerId идентификатор владельца
     * @return количество магазинов пользователя
     */
    int countByOwnerId(Long ownerId);

    /**
     * Проверяет принадлежность магазина пользователю.
     *
     * @param storeId идентификатор магазина
     * @param ownerId идентификатор владельца
     * @return {@code true} если магазин принадлежит пользователю
     */
    boolean existsByIdAndOwnerId(Long storeId, Long ownerId);

    /**
     * Получить список идентификаторов магазинов пользователя.
     *
     * @param ownerId идентификатор владельца
     * @return список идентификаторов его магазинов
     */
    @Query("SELECT s.id FROM Store s WHERE s.owner.id = :ownerId")
    List<Long> findStoreIdsByOwnerId(@Param("ownerId") Long ownerId);

}
