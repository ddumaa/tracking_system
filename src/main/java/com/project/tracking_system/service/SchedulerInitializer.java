package com.project.tracking_system.service;

import com.project.tracking_system.service.analytics.StatsAggregationService;
import com.project.tracking_system.service.telegram.TelegramReminderScheduler;
import com.project.tracking_system.service.jsonEvropostService.JwtTokenManager;
import com.project.tracking_system.service.user.SubscriptionExpirationScheduler;
import com.project.tracking_system.service.user.TokenCleanupService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Инициализирует задачи планировщика при старте приложения.
 */
@Service
@RequiredArgsConstructor
public class SchedulerInitializer {

    public static final long STATS_AGGREGATION_ID = 1L;
    public static final long TELEGRAM_REMINDER_ID = 2L;
    public static final long JWT_REFRESH_ID = 3L;
    public static final long SUBSCRIPTION_CHECK_ID = 4L;
    public static final long TOKEN_CLEANUP_ID = 5L;

    private final DynamicSchedulerService schedulerService;
    private final StatsAggregationService statsAggregationService;
    private final TelegramReminderScheduler telegramReminderScheduler;
    private final JwtTokenManager jwtTokenManager;
    private final SubscriptionExpirationScheduler subscriptionExpirationScheduler;
    private final TokenCleanupService tokenCleanupService;

    /**
     * Регистрирует все задачи в динамическом планировщике.
     */
    @PostConstruct
    public void register() {
        schedulerService.registerTask(STATS_AGGREGATION_ID, statsAggregationService::aggregateYesterday);
        schedulerService.registerTask(TELEGRAM_REMINDER_ID, telegramReminderScheduler::sendReminders);
        schedulerService.registerTask(JWT_REFRESH_ID, jwtTokenManager::scheduledTokenRefresh);
        schedulerService.registerTask(SUBSCRIPTION_CHECK_ID, subscriptionExpirationScheduler::checkExpiredSubscriptions);
        schedulerService.registerTask(TOKEN_CLEANUP_ID, tokenCleanupService::cleanupExpiredTokens);
    }
}
