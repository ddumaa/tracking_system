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

    /**
     * Получить магазины пользователя вместе с Telegram-настройками.
     *
     * @param ownerId идентификатор владельца
     * @return список магазинов с инициализированными настройками
     */
    @Query("SELECT DISTINCT s FROM Store s " +
           "LEFT JOIN FETCH s.telegramSettings ts " +
           "LEFT JOIN FETCH ts.templates " +
           "WHERE s.owner.id = :ownerId")
    List<Store> findByOwnerIdFetchSettings(@Param("ownerId") Long ownerId);

    /**
     * Получить все магазины с Telegram-настройками и подпиской владельца.
     *
     * @return список магазинов
     */
    @Query("SELECT s FROM Store s " +
           "LEFT JOIN FETCH s.telegramSettings " +
           "LEFT JOIN FETCH s.owner o " +
           "LEFT JOIN FETCH o.subscription us " +
           "LEFT JOIN FETCH us.subscriptionPlan")
    List<Store> findAllWithSettingsAndSubscription();

}
