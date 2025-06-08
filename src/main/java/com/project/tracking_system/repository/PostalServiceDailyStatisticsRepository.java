package com.project.tracking_system.repository;

import com.project.tracking_system.entity.PostalServiceDailyStatistics;
import com.project.tracking_system.entity.PostalServiceType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Репозиторий для ежедневной статистики по почтовым службам.
 */
public interface PostalServiceDailyStatisticsRepository extends JpaRepository<PostalServiceDailyStatistics, Long> {

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
}
