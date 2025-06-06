package com.project.tracking_system.service.analytics;

import com.project.tracking_system.entity.Store;
import com.project.tracking_system.entity.StoreStatistics;
import com.project.tracking_system.repository.StoreAnalyticsRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.List;
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

    /**
     * Получает аналитику по всем магазинам пользователя.
     */
    public List<StoreStatistics> getUserStatistics(Long userId) {
        log.info("📊 Получаем статистику по всем магазинам пользователя ID: {}", userId);
        return storeAnalyticsRepository.findAllByUserId(userId);
    }

    /**
     * Получает аналитику по конкретному магазину.
     */
    public Optional<StoreStatistics> getStoreStatistics(Long storeId) {
        log.info("📊 Получаем статистику по магазину ID: {}", storeId);
        return storeAnalyticsRepository.findByStoreId(storeId);
    }

    /**
     * Обновляет поле updatedAt (по расписанию или вручную).
     */
    @Transactional
    public void updateStoreAnalytics(Long storeId) {
        log.info("⚙️ Обновление аналитики магазина ID: {}", storeId);

        StoreStatistics stats = storeAnalyticsRepository.findByStoreId(storeId)
                .orElseThrow(() -> new IllegalStateException("Статистика не найдена для магазина ID=" + storeId));

        stats.setUpdatedAt(ZonedDateTime.now());
        storeAnalyticsRepository.save(stats);

        log.info("✅ Обновление завершено для магазина: {}", stats.getStore().getName());
    }

    /**
     * Агрегирует аналитику по нескольким магазинам (для блока "Общая аналитика").
     */
    public StoreStatistics aggregateStatistics(List<StoreStatistics> stats) {
        int totalSent = stats.stream().mapToInt(StoreStatistics::getTotalSent).sum();
        int totalDelivered = stats.stream().mapToInt(StoreStatistics::getTotalDelivered).sum();
        int totalReturned = stats.stream().mapToInt(StoreStatistics::getTotalReturned).sum();

        BigDecimal sumDelivery = stats.stream()
                .map(StoreStatistics::getSumDeliveryDays)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal sumPickup = stats.stream()
                .map(StoreStatistics::getSumPickupDays)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        StoreStatistics summary = new StoreStatistics();
        summary.setTotalSent(totalSent);
        summary.setTotalDelivered(totalDelivered);
        summary.setTotalReturned(totalReturned);
        summary.setSumDeliveryDays(sumDelivery);
        summary.setSumPickupDays(sumPickup);
        summary.setUpdatedAt(ZonedDateTime.now());

        Store virtualStore = new Store();
        virtualStore.setName("Все магазины");
        summary.setStore(virtualStore);

        return summary;
    }
}
