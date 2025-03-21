package com.project.tracking_system.service.analytics;

import com.project.tracking_system.entity.Store;
import com.project.tracking_system.entity.StoreStatistics;
import com.project.tracking_system.entity.GlobalStatus;
import com.project.tracking_system.repository.DeliveryHistoryRepository;
import com.project.tracking_system.repository.StoreAnalyticsRepository;
import com.project.tracking_system.repository.StoreRepository;
import com.project.tracking_system.repository.TrackParcelRepository;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
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
    private final StoreRepository storeRepository;
    private final TrackParcelRepository trackParcelRepository;
    private final DeliveryHistoryRepository deliveryHistoryRepository;

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

}