package com.project.tracking_system.repository;

import com.project.tracking_system.entity.TrackParcel;
import com.project.tracking_system.entity.GlobalStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface TrackParcelRepository extends JpaRepository<TrackParcel, Long> {

    List<TrackParcel> findByUserId(Long userId);

    /**
     * Найти все посылки пользователя с указанным порядком сортировки по дате.
     *
     * @param userId идентификатор пользователя
     * @param sort   настройка порядка сортировки
     * @return отсортированный список посылок
     */
    List<TrackParcel> findByUserId(Long userId, Sort sort);

    TrackParcel findByNumberAndUserId(String number, Long userId);

    List<TrackParcel> findByNumberInAndUserId(List<String> numbers, Long userId);
    List<TrackParcel> findByIdInAndUserId(List<Long> ids, Long userId);

    TrackParcel findByNumberAndStoreIdAndUserId(String number, Long storeId, Long userId);

    Page<TrackParcel> findByUserId(Long userId, Pageable pageable);

    // Поиск всех посылок по магазину
    List<TrackParcel> findByStoreId(Long storeId);

    boolean existsByNumberAndUserId(String number, Long userId);

    // Поиск посылок по списку магазинов с пагинацией.
    Page<TrackParcel> findByStoreIdIn(List<Long> storeIds, Pageable pageable);

    // Поиск одной посылки по номеру в рамках конкретного магазина
    TrackParcel findByNumberAndStoreId(String number, Long storeId);

    /**
     * Найти все предзарегистрированные посылки.
     *
     * @param pageable настройки пагинации
     * @return страница предзарегистрированных посылок
     */
    Page<TrackParcel> findByPreRegisteredTrue(Pageable pageable);

    /**
     * Найти предварительно зарегистрированную посылку по идентификатору.
     * <p>
     * Использование идентификатора гарантирует корректную работу,
     * даже если трек-номер ещё не присвоен.
     * </p>
     *
     * @param id идентификатор посылки
     * @return посылка или {@code null}, если не найдена
     */
    TrackParcel findByIdAndPreRegisteredTrue(Long id);

    /**
     * Обновить трек-номер предварительно зарегистрированной посылки.
     * <p>
     * Записи с уже указанным номером игнорируются, чтобы избежать
     * перезаписи существующих данных.
     * </p>
     *
     * @param id     идентификатор посылки
     * @param number новый трек-номер
     */
    @Modifying
    @Transactional
    @Query("UPDATE TrackParcel t SET t.number = :number WHERE t.id = :id AND t.preRegistered = true AND t.number IS NULL")
    void updatePreRegisteredNumber(@Param("id") Long id, @Param("number") String number);

    /**
     * Найти посылки по статусу с пагинацией.
     *
     * @param status   статус посылки
     * @param pageable настройки пагинации
     * @return страница посылок с указанным статусом
     */
    Page<TrackParcel> findByStatus(GlobalStatus status, Pageable pageable);

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

    List<TrackParcel> findByEpisodeIdAndUserIdOrderByTimestampAsc(Long episodeId, Long userId);

    /**
     * Получить все посылки с загруженными пользователем и магазином.
     *
     * @return список посылок
     */
    @Query("SELECT t FROM TrackParcel t JOIN FETCH t.store JOIN FETCH t.user JOIN FETCH t.episode")
    List<TrackParcel> findAllWithStoreAndUser();

    /**
     * Получить все посылки постранично с подгруженными магазином и пользователем.
     *
     * @param pageable настройки пагинации
     * @return страница посылок
     */
    @Query(value = "SELECT t FROM TrackParcel t JOIN FETCH t.store JOIN FETCH t.user JOIN FETCH t.episode",
           countQuery = "SELECT count(t) FROM TrackParcel t")
    Page<TrackParcel> findAllWithStoreAndUser(Pageable pageable);

    /**
     * Найти посылку по номеру с подгруженными магазином и пользователем.
     * <p>
     * Посылки без номера игнорируются, поэтому {@code NULL} значения
     * никогда не будут возвращены.
     * </p>
     *
     * @param number номер посылки
     * @return посылка или {@code null}, если не найдена
     */
    @Query("""
            SELECT t FROM TrackParcel t
            JOIN FETCH t.store
            JOIN FETCH t.user
            JOIN FETCH t.episode
            WHERE t.number = :number AND t.number IS NOT NULL
            """)
    TrackParcel findByNumberWithStoreAndUser(@Param("number") String number);

    /**
     * Найти посылку по идентификатору с подгруженными магазином и пользователем.
     *
     * @param id идентификатор посылки
     * @return посылка или {@code null}, если не найдена
     */
    @Query("SELECT t FROM TrackParcel t JOIN FETCH t.store JOIN FETCH t.user JOIN FETCH t.episode WHERE t.id = :id")
    TrackParcel findByIdWithStoreAndUser(@Param("id") Long id);

    /**
     * Находит последнюю созданную обменную посылку для указанной исходной отправки.
     *
     * @param original исходная посылка, инициировавшая обмен
     * @return самая поздняя обменная посылка или {@code null}, если обменов не было
     */
    TrackParcel findTopByReplacementOfOrderByTimestampDesc(TrackParcel original);

    /**
     * Очистить связь покупателя у его посылок.
     *
     * @param customerId идентификатор покупателя
     */
    @Modifying
    @Transactional
    @Query("UPDATE TrackParcel t SET t.customer = null WHERE t.customer.id = :customerId")
    void clearCustomer(@Param("customerId") Long customerId);

    /**
     * Получить уникальные названия магазинов для покупателя.
     *
     * @param customerId идентификатор покупателя
     * @return список названий магазинов
     */
    @Query("SELECT DISTINCT t.store.name FROM TrackParcel t WHERE t.customer.id = :customerId")
    List<String> findDistinctStoreNamesByCustomerId(@Param("customerId") Long customerId);

    /**
     * Подсчитать количество посылок покупателя в указанном статусе.
     *
     * @param customerId идентификатор покупателя
     * @param status     целевой статус
     * @return количество посылок
     */
    @Query("SELECT COUNT(t) FROM TrackParcel t WHERE t.customer.id = :customerId AND t.status = :status")
    int countByCustomerIdAndStatus(@Param("customerId") Long customerId,
                                   @Param("status") GlobalStatus status);

    /**
     * Поиск посылок по номеру, телефону или ФИО покупателя.
     * <p>
     * Выполняется частичное совпадение номера трека, номера телефона
     * (последний хранится без форматирования) и ФИО. Поиск по ФИО использует
     * до трёх токенов, каждый из которых должен встречаться в полном имени.
     * Посылки без трек-номера исключаются из поиска по номеру.
     * </p>
     *
     * @param storeIds    магазины владельца
     * @param userId      идентификатор пользователя
     * @param status      фильтр статуса (может быть {@code null})
     * @param query       фрагмент трек-номера
     * @param phoneDigits цифры телефона без форматирования
     * @param nameToken1  первый токен ФИО
     * @param nameToken2  второй токен ФИО
     * @param nameToken3  третий токен ФИО
     * @param pageable    настройки пагинации
     * @return страница посылок, удовлетворяющих условиям
     */
    @Query("""
            SELECT t FROM TrackParcel t
            LEFT JOIN t.customer c
            WHERE t.user.id = :userId
              AND t.store.id IN :storeIds
              AND (:status IS NULL OR t.status = :status)
              AND (
                    (t.number IS NOT NULL AND LOWER(t.number) LIKE LOWER(CONCAT('%', :query, '%')))
                    OR (:phoneDigits <> '' AND c.phone LIKE CONCAT('%', :phoneDigits, '%'))
                    OR (
                        :nameToken1 <> ''
                        AND c.fullName IS NOT NULL
                        AND LOWER(c.fullName) LIKE CONCAT('%', :nameToken1, '%')
                        AND (:nameToken2 = '' OR LOWER(c.fullName) LIKE CONCAT('%', :nameToken2, '%'))
                        AND (:nameToken3 = '' OR LOWER(c.fullName) LIKE CONCAT('%', :nameToken3, '%'))
                    )
              )
            """)
    Page<TrackParcel> searchByNumberPhoneOrName(@Param("storeIds") List<Long> storeIds,
                                                @Param("userId") Long userId,
                                                @Param("status") GlobalStatus status,
                                                @Param("query") String query,
                                                @Param("phoneDigits") String phoneDigits,
                                                @Param("nameToken1") String nameToken1,
                                                @Param("nameToken2") String nameToken2,
                                                @Param("nameToken3") String nameToken3,
                                                Pageable pageable);

}