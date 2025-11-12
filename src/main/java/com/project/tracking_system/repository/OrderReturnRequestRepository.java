package com.project.tracking_system.repository;

import com.project.tracking_system.entity.OrderReturnRequest;
import com.project.tracking_system.entity.OrderReturnRequestStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Репозиторий заявок на возврат/обмен.
 */
public interface OrderReturnRequestRepository extends JpaRepository<OrderReturnRequest, Long> {

    /**
     * Возвращает заявку по идемпотентному ключу без учёта регистра.
     */
    @Query("""
            select r from OrderReturnRequest r
            where lower(r.idempotencyKey) = lower(:idempotencyKey)
            """)
    Optional<OrderReturnRequest> findByIdempotencyKey(@Param("idempotencyKey") String idempotencyKey);

    /**
     * Ищет активную заявку по посылке и статусам.
     */
    Optional<OrderReturnRequest> findFirstByParcel_IdAndStatusIn(Long parcelId, Collection<OrderReturnRequestStatus> statuses);

    /**
     * Проверяет, существует ли в эпизоде заявка с указанным статусом.
     */
    boolean existsByEpisode_IdAndStatus(Long episodeId, OrderReturnRequestStatus status);

    /**
     * Возвращает идентификаторы посылок пользователя, по которым есть заявки в статусе ожидания решения.
     */
    @Query("""
            select r.parcel.id from OrderReturnRequest r
            where r.parcel.user.id = :userId and r.status = :status
            """)
    List<Long> findParcelIdsByUserAndStatus(@Param("userId") Long userId,
                                            @Param("status") OrderReturnRequestStatus status);

    /**
     * Возвращает идентификаторы посылок покупателя, по которым есть активные заявки.
     */
    @Query("""
            select r.parcel.id from OrderReturnRequest r
            where r.parcel.customer.id = :customerId and r.status in :statuses
            """)
    List<Long> findParcelIdsByCustomerAndStatusIn(@Param("customerId") Long customerId,
                                                  @Param("statuses") Collection<OrderReturnRequestStatus> statuses);

    /**
     * Возвращает активные заявки пользователя вместе с данными посылок.
     */
    @Query("""
            select distinct r from OrderReturnRequest r
            join fetch r.parcel p
            join fetch p.store
            join fetch r.episode
            where p.user.id = :userId and r.status in :statuses
            order by r.createdAt desc
            """)
    List<OrderReturnRequest> findActiveRequestsWithDetails(@Param("userId") Long userId,
                                                           @Param("statuses") Collection<OrderReturnRequestStatus> statuses);

    /**
     * Возвращает активные заявки покупателя с данными посылок и магазинов.
     */
    @Query("""
            select distinct r from OrderReturnRequest r
            join fetch r.parcel p
            left join fetch p.store
            where p.customer.id = :customerId and r.status in :statuses
            order by r.createdAt desc
            """)
    List<OrderReturnRequest> findActiveRequestsByCustomerWithDetails(@Param("customerId") Long customerId,
                                                                     @Param("statuses") Collection<OrderReturnRequestStatus> statuses);

    /**
     * Удаляет все заявки, связанные с указанными посылками.
     *
     * @param parcelIds идентификаторы посылок
     * @return количество удалённых записей
     */
    long deleteByParcel_IdIn(Collection<Long> parcelIds);

    /**
     * Возвращает заявку вместе с ключевыми связями для отображения в API.
     * <p>
     * Метод подгружает посылку, магазин и ответственного менеджера одним запросом,
     * чтобы предотвратить LazyInitializationException при маппинге за пределами транзакции.
     * </p>
     *
     * @param id идентификатор заявки
     * @return заявка с инициализированными связями или {@link Optional#empty()}, если она не найдена
     */
    @Query("""
            select r from OrderReturnRequest r
            join fetch r.parcel p
            join fetch p.user
            join fetch r.store
            left join fetch r.responsibleManager
            where r.id = :id
            """)
    Optional<OrderReturnRequest> findByIdWithDetails(@Param("id") Long id);

    /**
     * Возвращает заявку по идемпотентному ключу с ключевыми связями.
     * <p>
     * Используется REST-слоем, где требуется один запрос для загрузки заявки,
     * связанной посылки и магазина, чтобы избежать дополнительных обращений к БД.
     * </p>
     */
    @Query("""
            select r from OrderReturnRequest r
            join fetch r.parcel p
            join fetch p.user
            join fetch r.store
            left join fetch r.responsibleManager
            where lower(r.idempotencyKey) = lower(:idempotencyKey)
            """)
    Optional<OrderReturnRequest> findByIdempotencyKeyWithDetails(@Param("idempotencyKey") String idempotencyKey);
}

