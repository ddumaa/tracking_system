package com.project.tracking_system.repository;

import com.project.tracking_system.entity.PostalServiceType;
import com.project.tracking_system.entity.PostalServiceYearlyStatistics;
import com.project.tracking_system.repository.DeletableByStoreOrUser;
import java.util.Optional;

/**
 * Репозиторий для годовой статистики по почтовым службам.
 */
public interface PostalServiceYearlyStatisticsRepository
        extends DeletableByStoreOrUser<PostalServiceYearlyStatistics, Long> {

    /**
     * Найти статистику почтовой службы за конкретный год.
     *
     * @param storeId          идентификатор магазина
     * @param postalServiceType тип почтовой службы
     * @param periodYear       год статистики
     * @param periodNumber     номер периода (для года всегда 1)
     * @return статистика почтовой службы за год, если найдена
     */
    Optional<PostalServiceYearlyStatistics> findByStoreIdAndPostalServiceTypeAndPeriodYearAndPeriodNumber(Long storeId, PostalServiceType postalServiceType, int periodYear, int periodNumber);

    /**
     * Удалить годовую статистику конкретного магазина.
     */
    // Методы удаления определены в DeletableByStoreOrUser
}
