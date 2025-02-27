package com.project.tracking_system.service.user;

import com.project.tracking_system.entity.SubscriptionPlan;
import com.project.tracking_system.entity.User;
import com.project.tracking_system.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
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

    private final UserRepository userRepository;

    @Scheduled(cron = "0 0 3 * * *", zone = "UTC")
    public void checkExpiredSubscriptions() {
        ZonedDateTime nowUtc = ZonedDateTime.now(ZoneOffset.UTC);

        // Получаем пользователей с истекшей подпиской
        List<User> expiredUsers = userRepository.findUsersWithExpiredSubscription(nowUtc);
        List<User> usersToUpdate = new ArrayList<>();

        for (User user : expiredUsers) {
            user.setSubscriptionPlan(getFreePlan()); // Переключаем на бесплатный план
            user.setSubscriptionEndDate(null); // Обнуляем дату окончания подписки
            usersToUpdate.add(user);
            log.info("Пользователь с ID {} переведен на бесплатную подписку.", user.getId());
        }

        // Обновляем только измененных пользователей
        if (!usersToUpdate.isEmpty()) {
            userRepository.saveAll(usersToUpdate);
            log.info("Обновлены {} пользователей с истекшей подпиской.", usersToUpdate.size());
        }
    }

    private SubscriptionPlan getFreePlan() {
        // Можно внедрить SubscriptionPlanRepository и получать подписку из БД
        return new SubscriptionPlan(1L, "FREE", 10, 10, 10, false);
    }
}