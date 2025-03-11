package com.project.tracking_system.service.statistics;

import com.project.tracking_system.entity.StoreStatistics;
import com.project.tracking_system.model.GlobalStatus;
import com.project.tracking_system.repository.StoreStatisticsRepository;
import com.project.tracking_system.repository.TrackParcelRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.List;

/**
 * @author Dmitriy Anisimov
 * @date 11.03.2025
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class StoreStatisticsService {

    private final TrackParcelRepository parcelRepository;
    private final StoreStatisticsRepository storeStatisticsRepository;


    /**
     * Обновляет статистику для конкретного магазина.
     *
     * @param storeId ID магазина, для которого пересчитывается статистика.
     */
    @Transactional
    public void updateStoreStatistics(Long storeId) {
        int totalSent = parcelRepository.countByStoreId(storeId);
        int totalDelivered = parcelRepository.countByStoreIdAndStatus(storeId, GlobalStatus.DELIVERED);
        int totalReturned = parcelRepository.countByStoreIdAndStatus(storeId, GlobalStatus.RETURNED_TO_SENDER);
        Double avgDeliveryDays = parcelRepository.findAverageDeliveryTimeForStore(storeId);

        StoreStatistics stats = storeStatisticsRepository.findByStoreId(storeId)
                .orElse(new StoreStatistics());

        stats.setTotalSent(totalSent);
        stats.setTotalDelivered(totalDelivered);
        stats.setTotalReturned(totalReturned);
        stats.setAverageDeliveryDays(avgDeliveryDays);
        stats.setUpdatedAt(ZonedDateTime.now());

        storeStatisticsRepository.save(stats);
    }

    /**
     * Обновляет статистику для всех магазинов (используется в расписании).
     */
    @Transactional
    public void updateAllStoresStatistics() {
        List<Long> storeIds = storeStatisticsRepository.findAllStoreIds();
        for (Long storeId : storeIds) {
            updateStoreStatistics(storeId);
        }
    }

}