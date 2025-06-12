package com.project.tracking_system.repository;

import com.project.tracking_system.entity.PostalServiceDailyStatistics;
import com.project.tracking_system.entity.PostalServiceType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import com.project.tracking_system.repository.DeletableByStoreOrUser;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.math.BigDecimal;

/**
 * Репозиторий для ежедневной статистики по почтовым службам.
 */
public interface PostalServiceDailyStatisticsRepository
        extends JpaRepository<PostalServiceDailyStatistics, Long>,
        DeletableByStoreOrUser<PostalServiceDailyStatistics, Long> {

    /**
     * Найти статистику почтовой службы магазина за конкретную дату.
     *
     * @param storeId           идентификатор магазина
     * @param postalServiceType тип почтовой службы
     * @param date              дата статистики
     * @return ежедневная статистика, если найдена
     */
    Optional<PostalServiceDailyStatistics> findByStoreIdAndPostalServiceTypeAndDate(Long storeId,
                                                                                   PostalServiceType postalServiceType,
                                                                                   LocalDate date);

    /**
     * Найти статистику почтовой службы магазина за диапазон дат.
     *
     * @param storeId            идентификатор магазина
     * @param postalServiceType  тип почтовой службы
     * @param from               дата начала (включительно)
     * @param to                 дата окончания (включительно)
     * @return список ежедневной статистики
     */
    List<PostalServiceDailyStatistics> findByStoreIdAndPostalServiceTypeAndDateBetween(Long storeId,
                                                                                       PostalServiceType postalServiceType,
                                                                                       LocalDate from,
                                                                                       LocalDate to);

    /**
     * Найти статистику почтовой службы сразу для нескольких магазинов за диапазон дат.
     *
     * @param storeIds           идентификаторы магазинов
     * @param postalServiceType  тип почтовой службы
     * @param from               дата начала (включительно)
     * @param to                 дата окончания (включительно)
     * @return список ежедневной статистики
     */
    List<PostalServiceDailyStatistics> findByStoreIdInAndPostalServiceTypeAndDateBetween(List<Long> storeIds,
        PostalServiceType postalServiceType,
        LocalDate from,
        LocalDate to);

    /**
     * Найти статистику по всем магазинам и службам на конкретную дату.
     *
     * @param date дата статистики
     * @return список ежедневной статистики
     */
    List<PostalServiceDailyStatistics> findByDate(LocalDate date);

    /**
     * Атомарно увеличивает ежедневный счётчик отправлений для почтовой службы.
     *
     * @param storeId идентификатор магазина
     * @param postalServiceType тип почтовой службы
     * @param date дата статистики
     * @param delta величина увеличения
     * @return количество обновлённых записей
     */
    @Modifying
    @Transactional
    @Query("""
        UPDATE PostalServiceDailyStatistics p
        SET p.sent = p.sent + :delta,
            p.updatedAt = CURRENT_TIMESTAMP
        WHERE p.store.id = :storeId AND p.postalServiceType = :postalServiceType AND p.date = :date
        """)
    int incrementSent(@Param("storeId") Long storeId,
                      @Param("postalServiceType") PostalServiceType postalServiceType,
                      @Param("date") LocalDate date,
                      @Param("delta") int delta);

    /**
     * Атомарно увеличивает счётчик доставленных посылок за день.
     *
     * @param storeId        идентификатор магазина
     * @param postalServiceType тип почтовой службы
     * @param date           дата статистики
     * @param delta          величина увеличения
     * @param deliveryDays   добавляемые дни доставки
     * @param pickupDays     добавляемые дни ожидания
     * @return количество обновлённых записей
     */
    @Modifying
    @Transactional
    @Query("""
        UPDATE PostalServiceDailyStatistics p
        SET p.delivered = p.delivered + :delta,
            p.sumDeliveryDays = p.sumDeliveryDays + :deliveryDays,
            p.sumPickupDays = p.sumPickupDays + :pickupDays,
            p.updatedAt = CURRENT_TIMESTAMP
        WHERE p.store.id = :storeId AND p.postalServiceType = :postalServiceType AND p.date = :date
        """)
    int incrementDelivered(@Param("storeId") Long storeId,
                           @Param("postalServiceType") PostalServiceType postalServiceType,
                           @Param("date") LocalDate date,
                           @Param("delta") int delta,
                           @Param("deliveryDays") java.math.BigDecimal deliveryDays,
                           @Param("pickupDays") java.math.BigDecimal pickupDays);

    /**
     * Атомарно увеличивает счётчик возвращённых посылок за день.
     *
     * @param storeId        идентификатор магазина
     * @param postalServiceType тип почтовой службы
     * @param date           дата статистики
     * @param delta          величина увеличения
     * @param deliveryDays   добавляемые дни доставки
     * @param pickupDays     добавляемые дни нахождения на пункте
     * @return количество обновлённых записей
     */
    @Modifying
    @Transactional
    @Query("""
        UPDATE PostalServiceDailyStatistics p
        SET p.returned = p.returned + :delta,
            p.sumDeliveryDays = p.sumDeliveryDays + :deliveryDays,
            p.sumPickupDays = p.sumPickupDays + :pickupDays,
            p.updatedAt = CURRENT_TIMESTAMP
        WHERE p.store.id = :storeId AND p.postalServiceType = :postalServiceType AND p.date = :date
        """)
    int incrementReturned(@Param("storeId") Long storeId,
                          @Param("postalServiceType") PostalServiceType postalServiceType,
                          @Param("date") LocalDate date,
                          @Param("delta") int delta,
                          @Param("deliveryDays") java.math.BigDecimal deliveryDays,
                          @Param("pickupDays") java.math.BigDecimal pickupDays);

}
