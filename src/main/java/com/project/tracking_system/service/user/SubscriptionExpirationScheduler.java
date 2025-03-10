package com.project.tracking_system.service.user;

import com.project.tracking_system.entity.SubscriptionPlan;
import com.project.tracking_system.entity.UserSubscription;
import com.project.tracking_system.repository.SubscriptionPlanRepository;
import com.project.tracking_system.repository.UserSubscriptionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;

/**
 * Метод проверки роли пользователей
 * и срока её истечения, если срок истечения наступил
 * изменяет роль
 * <p>
 * каждую ночь в 3:00 UTC
 *
 * @author Dmitriy Anisimov
 * @date 07.02.2025
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class SubscriptionExpirationScheduler {

    private final SubscriptionPlanRepository subscriptionPlanRepository;
    private final UserSubscriptionRepository userSubscriptionRepository;

    @Scheduled(cron = "0 0 3 * * *", zone = "UTC")
    public void checkExpiredSubscriptions() {
        ZonedDateTime nowUtc = ZonedDateTime.now(ZoneOffset.UTC);

        // Получаем пользователей с истекшей подпиской
        List<UserSubscription> expiredSubscriptions = userSubscriptionRepository.findExpiredSubscriptions(nowUtc);

        if (expiredSubscriptions.isEmpty()) {
            log.info("Нет пользователей с истекшими подписками.");
            return;
        }

        SubscriptionPlan freePlan = getFreePlan();

        // Обновляем подписки
        for (UserSubscription subscription : expiredSubscriptions) {
            subscription.setSubscriptionPlan(freePlan); // Переключаем на бесплатный план
            subscription.setSubscriptionEndDate(null); // Обнуляем дату окончания подписки
            log.info("Пользователь с ID {} переведен на бесплатную подписку.", subscription.getUser().getId());
        }

        userSubscriptionRepository.saveAll(expiredSubscriptions);
        log.info("Обновлены {} подписок с истекшим сроком.", expiredSubscriptions.size());

    }

    private SubscriptionPlan getFreePlan() {
        return subscriptionPlanRepository.findByName("FREE")
                .orElseThrow(() -> new IllegalStateException("План FREE не найден в БД!"));
    }
}