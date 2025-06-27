package com.project.tracking_system.repository;

import com.project.tracking_system.entity.Customer;
import com.project.tracking_system.entity.BuyerReputation;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;
import java.util.Optional;

/**
 * Репозиторий для работы с сущностью {@link Customer}.
 */
public interface CustomerRepository extends JpaRepository<Customer, Long> {

    /**
     * Найти покупателя по номеру телефона.
     *
     * @param phone номер телефона в формате 375XXXXXXXXX
     * @return найденный покупатель или {@link java.util.Optional#empty()}
     */
    Optional<Customer> findByPhone(String phone);


    /**
     * Атомарно увеличить счётчик отправленных посылок.
     *
     * @param id идентификатор покупателя
     * @return количество обновлённых записей
     */
    @Modifying
    @Transactional
    @Query("""
        UPDATE Customer c
        SET c.sentCount = c.sentCount + 1
        WHERE c.id = :id
        """)
    int incrementSentCount(@Param("id") Long id);

    /**
     * Атомарно увеличить счётчик забранных посылок.
     *
     * @param id идентификатор покупателя
     * @return количество обновлённых записей
     */
    @Modifying
    @Transactional
    @Query("""
        UPDATE Customer c
        SET c.pickedUpCount = c.pickedUpCount + 1
        WHERE c.id = :id
        """)
    int incrementPickedUpCount(@Param("id") Long id);

    /**
     * Атомарно увеличить счётчик возвращённых посылок.
     *
     * @param id идентификатор покупателя
     * @return количество обновлённых записей
     */
    @Modifying
    @Transactional
    @Query("""
        UPDATE Customer c
        SET c.returnedCount = c.returnedCount + 1
        WHERE c.id = :id
        """)
    int incrementReturnedCount(@Param("id") Long id);

    /**
     * Подсчитать количество покупателей с указанной репутацией.
     *
     * @param reputation репутация покупателя
     * @return число покупателей
     */
    long countByReputation(BuyerReputation reputation);

    /**
     * Получить покупателей по репутации.
     *
     * @param reputation уровень доверия покупателя
     * @return список покупателей
     */
    List<Customer> findByReputation(BuyerReputation reputation);

