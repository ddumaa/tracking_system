package com.project.tracking_system.repository;

import com.project.tracking_system.entity.PostalServiceMonthlyStatistics;
import com.project.tracking_system.entity.PostalServiceType;
import org.springframework.data.jpa.repository.JpaRepository;
import com.project.tracking_system.repository.DeletableByStoreOrUser;
import java.util.Optional;

/**
 * Репозиторий для месячной статистики по почтовым службам.
 */
public interface PostalServiceMonthlyStatisticsRepository
        extends JpaRepository<PostalServiceMonthlyStatistics, Long>,
        DeletableByStoreOrUser<PostalServiceMonthlyStatistics, Long> {

    /**
     * Найти статистику почтовой службы за конкретный месяц.
     *
     * @param storeId          идентификатор магазина
     * @param postalServiceType тип почтовой службы
     * @param periodYear       год статистики
     * @param periodNumber     номер месяца
     * @return статистика почтовой службы за месяц, если найдена
     */
    Optional<PostalServiceMonthlyStatistics> findByStoreIdAndPostalServiceTypeAndPeriodYearAndPeriodNumber(Long storeId, PostalServiceType postalServiceType, int periodYear, int periodNumber);

    /**
     * Удалить месячную статистику конкретного магазина.
     */
    // Методы удаления определены в DeletableByStoreOrUser
}
