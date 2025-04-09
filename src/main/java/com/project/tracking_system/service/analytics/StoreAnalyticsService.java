package com.project.tracking_system.service.analytics;

import com.project.tracking_system.entity.Store;
import com.project.tracking_system.entity.StoreStatistics;
import com.project.tracking_system.entity.GlobalStatus;
import com.project.tracking_system.repository.StoreAnalyticsRepository;
import com.project.tracking_system.repository.StoreRepository;
import com.project.tracking_system.repository.TrackParcelRepository;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * @author Dmitriy Anisimov
 * @date 11.03.2025
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class StoreAnalyticsService {

    private final StoreAnalyticsRepository storeAnalyticsRepository;
    private final StoreRepository storeRepository;
    private final TrackParcelRepository trackParcelRepository;

    /**
     * Получает аналитику по всем магазинам пользователя.
     */
    public List<StoreStatistics> getUserStatistics(Long userId) {
        log.info("Получаем статистику по всем магазинам пользователя ID: {}", userId);
        return storeAnalyticsRepository.findAllByUserId(userId);
    }

    /**
     * Получает аналитику по конкретному магазину.
     */
    public Optional<StoreStatistics> getStoreStatistics(Long storeId) {
        log.info("Получаем статистику по магазину с ID: {}", storeId);
        return storeAnalyticsRepository.findByStoreId(storeId);
    }

    /**
     * Запускает обновление аналитики для всех магазинов (по расписанию).
     */
    @Transactional
    public void updateAllStoresAnalytics() {
        log.info("Запуск обновления аналитики для всех магазинов");

        List<Store> stores = storeRepository.findAll();
        for (Store store : stores) {
            updateStoreAnalytics(store.getId());
        }

        log.info("Обновление аналитики завершено");
    }

    /**
     * Обновляет аналитику для конкретного магазина.
     */
    @Transactional
    public void updateStoreAnalytics(Long storeId) {
        log.info("Обновляем аналитику для магазина ID: {}", storeId);

        // Загружаем магазин из БД
        Store store = storeRepository.findById(storeId)
                .orElseThrow(() -> new EntityNotFoundException("Магазин не найден: " + storeId));

        // Загружаем существующую статистику (мы уверены, что она уже есть!)
        StoreStatistics statistics = storeAnalyticsRepository.findByStoreId(storeId)
                .orElseThrow(() -> new IllegalStateException("Статистика для магазина ID=" + storeId + " не найдена!"));

        // Получаем реальные данные из БД
        statistics.setTotalSent(trackParcelRepository.countByStoreId(storeId));
        statistics.setTotalDelivered(trackParcelRepository.countByStoreIdAndStatus(storeId, GlobalStatus.DELIVERED));
        statistics.setTotalReturned(trackParcelRepository.countByStoreIdAndStatus(storeId, GlobalStatus.RETURNED));

        // Просто загружаем уже рассчитанное среднее время доставки
        log.info("Среднее время доставки для {}: {} дней", store.getName(), statistics.getAverageDeliveryDays());


        // Рассчитываем процент успешных доставок и возвратов
        statistics.setDeliverySuccessRate(calculateDeliverySuccessRate(statistics));
        statistics.setReturnRate(calculateReturnRate(statistics));

        storeAnalyticsRepository.save(statistics);
        log.info("Аналитика обновлена для магазина ID: {}", storeId);
    }

    /**
     * Рассчитывает процент успешных доставок.
     */
    private BigDecimal calculateDeliverySuccessRate(StoreStatistics statistics) {
        if (statistics.getTotalSent() == 0) return BigDecimal.ZERO;
        return BigDecimal.valueOf((statistics.getTotalDelivered() * 100.0) / statistics.getTotalSent());
    }

    /**
     * Рассчитывает процент возвратов.
     */
    private BigDecimal calculateReturnRate(StoreStatistics statistics) {
        if (statistics.getTotalSent() == 0) return BigDecimal.ZERO;
        return BigDecimal.valueOf((statistics.getTotalReturned() * 100.0) / statistics.getTotalSent());
    }

    public StoreStatistics aggregateStatistics(List<StoreStatistics> stats) {
        int totalSent = stats.stream().mapToInt(StoreStatistics::getTotalSent).sum();
        int totalDelivered = stats.stream().mapToInt(StoreStatistics::getTotalDelivered).sum();
        int totalReturned = stats.stream().mapToInt(StoreStatistics::getTotalReturned).sum();

        double avgDeliveryDays = stats.stream()
                .map(StoreStatistics::getAverageDeliveryDays)
                .filter(Objects::nonNull)                  // ← защита от null
                .filter(d -> d > 0)                        // ← фильтрация положительных
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(0.0);

        StoreStatistics summary = new StoreStatistics();
        summary.setTotalSent(totalSent);
        summary.setTotalDelivered(totalDelivered);
        summary.setTotalReturned(totalReturned);
        summary.setAverageDeliveryDays(avgDeliveryDays);

        double successRate = totalSent > 0 ? ((double) totalDelivered / totalSent) * 100 : 0.0;
        double returnRate = totalSent > 0 ? ((double) totalReturned / totalSent) * 100 : 0.0;

        summary.setDeliverySuccessRate(
                BigDecimal.valueOf(successRate).setScale(2, RoundingMode.HALF_UP)
        );
        summary.setReturnRate(
                BigDecimal.valueOf(returnRate).setScale(2, RoundingMode.HALF_UP)
        );

        Store virtualStore = new Store();
        virtualStore.setName("Все магазины");
        summary.setStore(virtualStore);

        return summary;
    }

}