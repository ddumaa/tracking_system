package com.project.tracking_system.service;

import com.project.tracking_system.entity.SubscriptionPlan;
import com.project.tracking_system.entity.User;
import com.project.tracking_system.repository.SubscriptionPlanRepository;
import com.project.tracking_system.repository.UserRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;

/**
 * @author Dmitriy Anisimov
 * @date 11.02.2025
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class SubscriptionService {

    private final TrackParcelService trackParcelService;
    private final UserRepository userRepository;
    private final SubscriptionPlanRepository subscriptionPlanRepository;

    private final String premiumPlan = "PREMIUM";
    private final String freePlan  = "FREE";

    public int canUploadTracks(Long userId, int tracksCount) {
        // Получаем название подписки пользователя
        String planName = userRepository.getSubscriptionPlanName(userId);
        if (planName == null) {
            return 0; // Если подписка не найдена, возвратим 0
        }

        // Получаем максимальное количество треков на файл для текущего плана
        Integer maxTracksPerFile = subscriptionPlanRepository.getMaxTracksPerFileByName(planName);
        if (maxTracksPerFile == null) {
            return Integer.MAX_VALUE; // Безлимитный план, если не найдено ограничений
        }

        return Math.min(tracksCount, maxTracksPerFile);
    }

    public int canSaveMoreTracks(Long userId, int tracksCountToSave) {
        // Получаем название подписки пользователя
        String planName = userRepository.getSubscriptionPlanName(userId);
        if (planName == null) {
            return 0; // Если подписка не найдена, возвратим 0
        }

        // Получаем максимальное количество сохраненных треков для текущего плана
        Integer maxSavedTracks = subscriptionPlanRepository.getMaxSavedTracksByName(planName);
        if (maxSavedTracks == null) {
            return Integer.MAX_VALUE; // Безлимитный план, если не найдено ограничений
        }

        // Получаем количество уже сохраненных треков для пользователя
        int currentSavedTracks = trackParcelService.getSavedTracksCountForUser(userId);
        int remainingTracks = maxSavedTracks - currentSavedTracks;

        // Возвращаем оставшиеся треки, которые можно сохранить
        return Math.max(0, remainingTracks);
    }

    public int canUpdateTracks(Long userId, int updatesMade) {
        // Получаем название подписки пользователя
        String planName = userRepository.getSubscriptionPlanName(userId);
        if (planName == null) {
            return 0; // Если подписка не найдена, возвратим 0
        }

        // Получаем количество обновлений и дату последнего обновления
        Object[] userUpdateData = userRepository.getUpdateCountAndLastUpdateDate(userId);
        int updateCount = (Integer) userUpdateData[0];
        ZonedDateTime lastUpdate = (ZonedDateTime) userUpdateData[1];

        // Получаем максимальное количество обновлений для текущего плана
        Integer maxUpdates = subscriptionPlanRepository.getMaxUpdatesByName(planName);
        if (maxUpdates == null) {
            return Integer.MAX_VALUE; // Безлимитный план, если не найдено ограничений
        }

        // Вычисляем оставшиеся обновления
        int remainingUpdates = maxUpdates - updateCount;

        return Math.max(0, remainingUpdates);
    }

    public boolean canUseBulkUpdate(Long userId) {
        String planName = userRepository.getSubscriptionPlanName(userId);
        return premiumPlan.equals(planName);
    }

    @Transactional
    public void upgradeOrExtendSubscription(Long userId, int months) {
        log.info("Попытка обновления подписки для пользователя с ID: {}", userId);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> {
                    log.warn("Пользователь с ID {} не найден", userId);
                    return new IllegalArgumentException("Пользователь не найден");
                });

        ZonedDateTime nowUtc = ZonedDateTime.now(ZoneOffset.UTC);

        // Получаем информацию о подписке пользователя
        SubscriptionPlan plan = user.getSubscriptionPlan();
        if (plan == null) {
            log.error("У пользователя {} отсутствует подписка!", userId);
            throw new IllegalStateException("У пользователя отсутствует подписка");
        }

        // Проверяем текущий план и продлеваем или обновляем подписку
        if (premiumPlan.equals(plan.getName())) {
            extendPremiumUserSubscription(user, months, nowUtc);
        } else if (freePlan.equals(plan.getName())) {
            upgradeToPremiumSubscription(user, months, nowUtc);
        } else {
            log.warn("Попытка обновления подписки пользователя {}, но его статус не позволяет это сделать", userId);
            throw new IllegalArgumentException("Пользователь в статусе, который нельзя апгрейдить");
        }

        userRepository.save(user);
        log.info("Подписка пользователя ID={} успешно обновлена. Новый план: {} до {}", userId, plan.getName(), user.getSubscriptionEndDate());
    }

    private void extendPremiumUserSubscription(User user, int months, ZonedDateTime nowUtc) {
        ZonedDateTime currentExpiry = user.getSubscriptionEndDate();
        if (currentExpiry == null || currentExpiry.isBefore(nowUtc)) {
            currentExpiry = nowUtc;
        }
        user.setSubscriptionEndDate(currentExpiry.plusMonths(months));
        log.info("Продление подписки пользователя {} до {}", user.getId(), user.getSubscriptionEndDate());
    }

    private void upgradeToPremiumSubscription(User user, int months, ZonedDateTime nowUtc) {
        SubscriptionPlan subscriptionPlan = subscriptionPlanRepository.findByName(premiumPlan)
                .orElseThrow(() -> new RuntimeException("План " + premiumPlan + " не найден"));

        user.setSubscriptionPlan(subscriptionPlan);
        user.setSubscriptionEndDate(nowUtc.plusMonths(months));

        log.info("Апгрейд пользователя {} до подписки {} с подпиской до {}", user.getId(), premiumPlan, user.getSubscriptionEndDate());
    }

    @Transactional
    public void changeSubscription(Long userId, String newPlanName, Integer months) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Пользователь не найден"));

        // Проверяем, существует ли новый план подписки
        SubscriptionPlan newPlan = subscriptionPlanRepository.findByName(newPlanName)
                .orElseThrow(() -> new IllegalArgumentException("Подписка не найдена"));

        // Устанавливаем новый план подписки
        user.setSubscriptionPlan(newPlan);

        // Если месяц не передан, ставим срок на 1 месяц по умолчанию
        ZonedDateTime nowUtc = ZonedDateTime.now(ZoneOffset.UTC);
        user.setSubscriptionEndDate(nowUtc.plusMonths(months));

        userRepository.save(user);
        log.info("Подписка пользователя с ID {} изменена на {} до {}", userId, newPlanName, user.getSubscriptionEndDate());
    }

}