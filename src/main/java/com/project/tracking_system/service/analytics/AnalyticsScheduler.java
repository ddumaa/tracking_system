package com.project.tracking_system.service.analytics;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * @author Dmitriy Anisimov
 * @date 11.03.2025
 */
@Slf4j
@RequiredArgsConstructor
@Component
public class AnalyticsScheduler {

    private final AnalyticsService statisticsService;

    @Scheduled(cron = "0 0 4 * * *") // Запускаем каждый день в 4:00 ночи
    public void scheduleAnalyticsUpdate() {
        statisticsService.updateAllStoresAnalytics();
    }

}