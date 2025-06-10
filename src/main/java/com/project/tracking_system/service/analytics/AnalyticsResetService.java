package com.project.tracking_system.service.analytics;

import com.project.tracking_system.repository.PostalServiceStatisticsRepository;
import com.project.tracking_system.repository.StoreAnalyticsRepository;
import com.project.tracking_system.repository.StoreDailyStatisticsRepository;
import com.project.tracking_system.repository.StoreWeeklyStatisticsRepository;
import com.project.tracking_system.repository.StoreMonthlyStatisticsRepository;
import com.project.tracking_system.repository.StoreYearlyStatisticsRepository;
import com.project.tracking_system.repository.PostalServiceDailyStatisticsRepository;
import com.project.tracking_system.repository.PostalServiceWeeklyStatisticsRepository;
import com.project.tracking_system.repository.PostalServiceMonthlyStatisticsRepository;
import com.project.tracking_system.repository.PostalServiceYearlyStatisticsRepository;
import com.project.tracking_system.service.store.StoreService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Сервис для удаления аналитики магазинов и служб доставки.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AnalyticsResetService {

    private final StoreAnalyticsRepository storeAnalyticsRepository;
    private final PostalServiceStatisticsRepository postalStatisticsRepository;
    private final StoreDailyStatisticsRepository storeDailyRepo;
    private final StoreWeeklyStatisticsRepository storeWeeklyRepo;
    private final StoreMonthlyStatisticsRepository storeMonthlyRepo;
    private final StoreYearlyStatisticsRepository storeYearlyRepo;
    private final PostalServiceDailyStatisticsRepository psDailyRepo;
    private final PostalServiceWeeklyStatisticsRepository psWeeklyRepo;
    private final PostalServiceMonthlyStatisticsRepository psMonthlyRepo;
    private final PostalServiceYearlyStatisticsRepository psYearlyRepo;
    private final StoreService storeService;

    /**
     * Удаляет всю аналитику пользователя по всем его магазинам.
     *
     * @param userId идентификатор пользователя
     */
    @Transactional
    public void resetAllAnalytics(Long userId) {
        log.info("\uD83D\uDD04 Сбрасываем аналитику пользователя ID={}", userId);

        // Обнуляем суммарные счётчики
        storeAnalyticsRepository.resetByUserId(userId);
        postalStatisticsRepository.resetByUserId(userId);

        // Удаляем агрегированные данные по периодам
        storeDailyRepo.deleteByUserId(userId);
        storeWeeklyRepo.deleteByUserId(userId);
        storeMonthlyRepo.deleteByUserId(userId);
        storeYearlyRepo.deleteByUserId(userId);

        psDailyRepo.deleteByUserId(userId);
        psWeeklyRepo.deleteByUserId(userId);
        psMonthlyRepo.deleteByUserId(userId);
        psYearlyRepo.deleteByUserId(userId);
    }

    /**
     * Удаляет аналитику одного магазина пользователя.
     * Предварительно проверяется принадлежность магазина пользователю.
     *
     * @param userId  идентификатор пользователя
     * @param storeId идентификатор магазина
     */
    @Transactional
    public void resetStoreAnalytics(Long userId, Long storeId) {
        storeService.checkStoreOwnership(storeId, userId);
        log.info("\uD83D\uDD04 Сбрасываем аналитику магазина ID={} пользователя ID={}", storeId, userId);

        // Обнуляем суммарные счётчики магазина
        storeAnalyticsRepository.resetByStoreId(storeId);
        postalStatisticsRepository.resetByStoreId(storeId);

        // Удаляем периодическую статистику
        storeDailyRepo.deleteByStoreId(storeId);
        storeWeeklyRepo.deleteByStoreId(storeId);
        storeMonthlyRepo.deleteByStoreId(storeId);
        storeYearlyRepo.deleteByStoreId(storeId);

        psDailyRepo.deleteByStoreId(storeId);
        psWeeklyRepo.deleteByStoreId(storeId);
        psMonthlyRepo.deleteByStoreId(storeId);
        psYearlyRepo.deleteByStoreId(storeId);

        log.info("Analytics reset for store {} by user {}", storeId, userId);
    }
}
