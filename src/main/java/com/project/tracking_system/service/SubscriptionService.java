package com.project.tracking_system.service;

import com.project.tracking_system.dto.UpdateInfoDto;
import com.project.tracking_system.entity.SubscriptionPlan;
import com.project.tracking_system.entity.User;
import com.project.tracking_system.repository.SubscriptionPlanRepository;
import com.project.tracking_system.repository.TrackParcelRepository;
import com.project.tracking_system.repository.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneId;
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

    private final TrackParcelRepository trackParcelRepository;
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
        int currentSavedTracks = trackParcelRepository.countByUserId(userId);
        int remainingTracks = maxSavedTracks - currentSavedTracks;

        // Возвращаем оставшиеся треки, которые можно сохранить
        return Math.max(0, remainingTracks);
    }

    public int canUpdateTracks(Long userId, int updatesRequested) {
        // Получаем информацию о подписке
        String planName = userRepository.getSubscriptionPlanName(userId);
        if (planName == null) return 0; // Если нет плана, обновления недоступны.

        // Проверяем лимиты
        Integer maxUpdates = subscriptionPlanRepository.getMaxUpdatesByName(planName);
        if (maxUpdates == null) return updatesRequested; // Безлимитный план

        // Получаем информацию о пользователе
        UpdateInfoDto updateInfo = userRepository.getUpdateInfo(userId);
        int usedUpdates = (updateInfo.updateCount() != null) ? updateInfo.updateCount() : 0;
        ZonedDateTime lastUpdate = (updateInfo.lastUpdate() != null) ? updateInfo.lastUpdate() : ZonedDateTime.now().minusDays(1);

        // Приводим `lastUpdate` к локальному часовому поясу сервера
        ZoneId systemZone = ZoneId.systemDefault(); // Часовой пояс сервера
        ZonedDateTime lastUpdateLocal = lastUpdate.withZoneSameInstant(systemZone);

        // Проверяем смену дня
        if (!lastUpdateLocal.toLocalDate().isEqual(LocalDate.now(systemZone))) {
            log.warn("⚠️ [resetUpdateCount] Сбрасываем updateCount для userId={} (время в БД: {}, текущее время: {})",
                    userId, lastUpdate, ZonedDateTime.now());
            log.info("Смена дня: сброс счётчика обновлений для пользователя {}", userId);
            userRepository.resetUpdateCount(userId, ZonedDateTime.now());
            usedUpdates = 0;
        }

        // Считаем, сколько обновлений осталось
        int remainingUpdates = Math.max(maxUpdates - usedUpdates, 0);

        // Возвращаем, сколько реально можно обновить
        return Math.min(remainingUpdates, updatesRequested);
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

        if (premiumPlan.equalsIgnoreCase(newPlanName)) {
            int subscriptionMonths = (months != null) ? months : 1; // По умолчанию 1 месяц
            user.setSubscriptionEndDate(ZonedDateTime.now(ZoneOffset.UTC).plusMonths(subscriptionMonths));
            log.info("Пользователь {} получил подписку {} до {}", userId, newPlanName, user.getSubscriptionEndDate());
        } else {
            user.setSubscriptionEndDate(null); // Убираем ограничение по сроку для бесплатного плана
            log.info("Пользователь {} переведен на бесплатный план {}", userId, newPlanName);
        }

        userRepository.save(user);
        log.info("Подписка пользователя с ID {} изменена на {} до {}", userId, newPlanName, user.getSubscriptionEndDate());
    }

}