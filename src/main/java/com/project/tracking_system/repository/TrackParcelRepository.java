package com.project.tracking_system.repository;

import com.project.tracking_system.entity.TrackParcel;
import com.project.tracking_system.entity.GlobalStatus;
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

    @Modifying
    @Transactional
    @Query("DELETE FROM TrackParcel t WHERE t.store.id = :storeId")
    void deleteByStoreId(@Param("storeId") Long storeId);

    /**
     * Найти посылки, ожидающие покупателя дольше указанной даты.
     *
     * @param status    статус посылки
     * @param threshold дата, ранее которой посылка прибыла на пункт выдачи
     * @return список подходящих посылок
     */
    @Query("""
        SELECT p FROM TrackParcel p
        JOIN DeliveryHistory h ON h.trackParcel.id = p.id
        WHERE p.status = :status
          AND h.arrivedDate < :threshold
        """)
    List<TrackParcel> findWaitingForPickupBefore(@Param("status") GlobalStatus status,
                                                 @Param("threshold") java.time.ZonedDateTime threshold);

    /**
     * Найти все активные посылки покупателя в указанных статусах.
     *
     * @param customerId идентификатор покупателя
     * @param statuses    список статусов
     * @return список подходящих посылок
     */
    List<TrackParcel> findByCustomerIdAndStatusIn(Long customerId, List<GlobalStatus> statuses);


    @Query("SELECT t FROM TrackParcel t WHERE t.customer.id = :customerId AND t.status NOT IN (:finalStatuses)")
    List<TrackParcel> findActiveByCustomerId(@Param("customerId") Long customerId,
                                             @Param("finalStatuses") List<GlobalStatus> finalStatuses);

    /**
     * Найти активные посылки покупателя в конкретном магазине.
     *
     * @param customerId идентификатор покупателя
     * @param storeId    идентификатор магазина
     * @param finalStatuses статусы, при которых посылка считается завершённой
     * @return список посылок
     */
    @Query("""
            SELECT t FROM TrackParcel t
            WHERE t.customer.id = :customerId
              AND t.store.id = :storeId
              AND t.status NOT IN (:finalStatuses)
            """)
    List<TrackParcel> findActiveByCustomerIdAndStoreId(@Param("customerId") Long customerId,
                                                      @Param("storeId") Long storeId,
                                                      @Param("finalStatuses") List<GlobalStatus> finalStatuses);

    /**
     * Получить все посылки с загруженными пользователем и магазином.
     *
     * @return список посылок
     */
    @Query("SELECT t FROM TrackParcel t JOIN FETCH t.store JOIN FETCH t.user")
    List<TrackParcel> findAllWithStoreAndUser();

    /**
     * Получить все посылки постранично с подгруженными магазином и пользователем.
     *
     * @param pageable настройки пагинации
     * @return страница посылок
     */
    @Query(value = "SELECT t FROM TrackParcel t JOIN FETCH t.store JOIN FETCH t.user",
           countQuery = "SELECT count(t) FROM TrackParcel t")
    Page<TrackParcel> findAllWithStoreAndUser(Pageable pageable);

    /**
     * Найти посылку по номеру с подгруженными магазином и пользователем.
     *
     * @param number номер посылки
     * @return посылка или {@code null}, если не найдена
     */
    @Query("SELECT t FROM TrackParcel t JOIN FETCH t.store JOIN FETCH t.user WHERE t.number = :number")
    TrackParcel findByNumberWithStoreAndUser(@Param("number") String number);

    /**
     * Найти посылку по идентификатору с подгруженными магазином и пользователем.
     *
     * @param id идентификатор посылки
     * @return посылка или {@code null}, если не найдена
     */
    @Query("SELECT t FROM TrackParcel t JOIN FETCH t.store JOIN FETCH t.user WHERE t.id = :id")
    TrackParcel findByIdWithStoreAndUser(@Param("id") Long id);

    /**
     * Очистить связь покупателя у его посылок.
     *
     * @param customerId идентификатор покупателя
     */
    @Modifying
    @Transactional
    @Query("UPDATE TrackParcel t SET t.customer = null WHERE t.customer.id = :customerId")
    void clearCustomer(@Param("customerId") Long customerId);

}