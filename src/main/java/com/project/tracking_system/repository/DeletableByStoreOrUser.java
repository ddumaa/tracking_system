package com.project.tracking_system.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.NoRepositoryBean;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

/**
 * Базовый репозиторий с методами удаления по магазину или пользователю.
 *
 * @param <T>  тип сущности
 * @param <ID> тип идентификатора
 */
@NoRepositoryBean
public interface DeletableByStoreOrUser<T, ID> extends JpaRepository<T, ID> {

    /**
     * Удалить все записи конкретного магазина.
     *
     * @param storeId идентификатор магазина
     */
    @Modifying
    @Transactional
    @Query("DELETE FROM #{#entityName} e WHERE e.store.id = :storeId")
    void deleteByStoreId(@Param("storeId") Long storeId);

    /**
     * Удалить все записи всех магазинов пользователя.
     *
     * @param userId идентификатор пользователя
     */
    @Modifying
    @Transactional
    @Query("DELETE FROM #{#entityName} e WHERE e.store.owner.id = :userId")
    void deleteByUserId(@Param("userId") Long userId);
}
