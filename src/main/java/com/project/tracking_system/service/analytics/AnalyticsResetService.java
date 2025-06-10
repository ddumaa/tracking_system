package com.project.tracking_system.service.analytics;

import com.project.tracking_system.repository.PostalServiceStatisticsRepository;
import com.project.tracking_system.repository.StoreAnalyticsRepository;
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
    private final StoreService storeService;

    /**
     * Удаляет всю аналитику пользователя по всем его магазинам.
     *
     * @param userId идентификатор пользователя
     */
    @Transactional
    public void resetAllAnalytics(Long userId) {
        log.info("\uD83D\uDD04 Удаляем всю аналитику пользователя ID={}", userId);
        storeAnalyticsRepository.deleteByUserId(userId);
        postalStatisticsRepository.deleteByUserId(userId);
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
        log.info("\uD83D\uDD04 Удаляем аналитику магазина ID={} пользователя ID={}", storeId, userId);
        storeAnalyticsRepository.deleteByStoreId(storeId);
        postalStatisticsRepository.deleteByStoreId(storeId);
    }
}
