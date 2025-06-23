package com.project.tracking_system.service;

import com.project.tracking_system.entity.SubscriptionPlan;
import com.project.tracking_system.entity.SubscriptionLimits;
import com.project.tracking_system.entity.User;
import com.project.tracking_system.entity.UserSubscription;
import com.project.tracking_system.model.subscription.FeatureKey;
import com.project.tracking_system.repository.SubscriptionPlanRepository;
import com.project.tracking_system.repository.TrackParcelRepository;
import com.project.tracking_system.repository.UserSubscriptionRepository;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Optional;
import java.math.BigDecimal;

/**
 * @author Dmitriy Anisimov
 * @date 11.02.2025
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class SubscriptionService {

    private final TrackParcelRepository trackParcelRepository;
    private final UserSubscriptionRepository userSubscriptionRepository;
    private final SubscriptionPlanRepository subscriptionPlanRepository;

    /**
     * Рассчитывает, сколько треков можно загрузить в одном файле.
     * <p>
     * Если подписка отсутствует, возвращается {@code 0}. Для безлимитного
     * плана метод вернёт запрошенное количество треков.
     * </p>
     *
     * @param userId      идентификатор пользователя
     * @param tracksCount запрошенное количество треков
     * @return максимально допустимое количество треков для загрузки
     */
    public int canUploadTracks(Long userId, int tracksCount) {
        // Получаем подписку пользователя
        Optional<UserSubscription> optionalSubscription = userSubscriptionRepository.findByUserId(userId);
        if (optionalSubscription.isEmpty()) {
            return 0; // Если подписки нет, загрузка невозможна
        }

        UserSubscription subscription = optionalSubscription.get();
        SubscriptionPlan plan = subscription.getSubscriptionPlan();
        SubscriptionLimits limits = (plan != null) ? plan.getLimits() : null;

        // Получаем лимит треков на файл
        Integer maxTracksPerFile = (limits != null) ? limits.getMaxTracksPerFile() : null;
        if (maxTracksPerFile == null) {
            return Integer.MAX_VALUE; // Безлимитный план
        }

        return Math.min(tracksCount, maxTracksPerFile);
    }

    /**
     * Проверяет, сколько новых треков пользователь может сохранить.
     * <p>
     * Лимит определяется активной подпиской и уже сохранённым количеством треков.
     * Если подписки нет, метод возвращает {@code 0}.
     * </p>
     *
     * @param userId            идентификатор пользователя
     * @param tracksCountToSave количество треков, которые требуется сохранить
     * @return допустимое количество треков для сохранения
     */
    public int canSaveMoreTracks(Long userId, int tracksCountToSave) {
        Optional<UserSubscription> optionalSubscription = userSubscriptionRepository.findByUserId(userId);
        if (optionalSubscription.isEmpty()) {
            log.warn("⛔ Пользователь {} не имеет активной подписки. Сохранение невозможно.", userId);
            return 0;
        }

        UserSubscription subscription = optionalSubscription.get();
        SubscriptionPlan plan = subscription.getSubscriptionPlan();
        SubscriptionLimits limits = (plan != null) ? plan.getLimits() : null;

        Integer maxSavedTracks = (limits != null) ? limits.getMaxSavedTracks() : null;
        if (maxSavedTracks == null) {
            log.info("✅ У пользователя {} безлимитный план. Можно сохранить {} треков.", userId, tracksCountToSave);
            return tracksCountToSave; // Безлимитный план позволяет сохранить все запрошенные треки
        }

        int currentSavedTracks = trackParcelRepository.countByUserId(userId);
        int remainingTracks = Math.max(0, maxSavedTracks - currentSavedTracks);

        // Возвращаем минимальное из возможного и запрошенного
        int allowedToSave = Math.min(remainingTracks, tracksCountToSave);

        log.info("🔄 Пользователь {} запросил сохранение {} треков. Доступно: {}. Разрешено сохранить: {}.",
                userId, tracksCountToSave, remainingTracks, allowedToSave);

        return allowedToSave;
    }


    /**
     * Определяет, сколько обновлений треков доступно пользователю сегодня.
     * <p>
     * При смене календарного дня счётчик обновлений сбрасывается. Отсутствие
     * подписки приводит к возвращению {@code 0}.
     * </p>
     *
     * @param userId          идентификатор пользователя
     * @param updatesRequested запрошенное количество обновлений
     * @return разрешённое количество обновлений
     */
    public int canUpdateTracks(Long userId, int updatesRequested) {
        // Получаем подписку пользователя
        UserSubscription subscription = userSubscriptionRepository.findByUserId(userId)
                .orElse(null);
        if (subscription == null) {
            log.warn("⛔ Пользователь {} не имеет активной подписки. Обновление невозможно.", userId);
            return 0; // Если подписки нет, обновления невозможны
        }

        SubscriptionPlan plan = subscription.getSubscriptionPlan();
        SubscriptionLimits limits = (plan != null) ? plan.getLimits() : null;
        Integer maxUpdates = (limits != null) ? limits.getMaxTrackUpdates() : null;
        if (maxUpdates == null) {
            log.info("✅ У пользователя {} безлимитный план. Разрешено {} обновлений.", userId, updatesRequested);
            return updatesRequested; // Безлимитный план
        }

        // Проверяем и сбрасываем лимиты, если день сменился
        LocalDate previousResetDate = subscription.getResetDate();
        subscription.checkAndResetLimits();

        // Если лимиты сбросились, логируем
        if (!previousResetDate.equals(subscription.getResetDate())) {
            log.warn("⚠️ [resetUpdateCount] Лимиты обновлений сброшены для userId={} (было: {}, стало: {}).",
                    userId, previousResetDate, subscription.getResetDate());
        }

        // Получаем текущее количество использованных обновлений
        int usedUpdates = subscription.getUpdateCount();
        int remainingUpdates = Math.max(maxUpdates - usedUpdates, 0);

        // Считаем, сколько обновлений можно выполнить
        int updatesAllowed = Math.min(remainingUpdates, updatesRequested);

        if (updatesAllowed > 0) {
            subscription.setUpdateCount(usedUpdates + updatesAllowed);
            userSubscriptionRepository.save(subscription);
            log.info("🔄 Пользователь {} запросил {} обновлений, разрешено: {} (использовано: {}).",
                    userId, updatesRequested, updatesAllowed, subscription.getUpdateCount());
        } else {
            log.warn("⛔ Пользователь {} достиг лимита обновлений: {}/{}", userId, usedUpdates, maxUpdates);
        }

        return updatesAllowed;
    }

    /**
     * Проверяет возможность массового обновления треков для пользователя.
     *
     * @param userId идентификатор пользователя
     * @return {@code true}, если тарифный план позволяет массовое обновление
     */
    public boolean canUseBulkUpdate(Long userId) {
        boolean allowed = isFeatureEnabled(userId, FeatureKey.BULK_UPDATE);
        log.debug("Пользователь {} пытается использовать массовое обновление. Доступ: {}", userId, allowed);
        return allowed;
    }

    /**
     * Проверяет, разрешено ли автообновление треков для пользователя.
     *
     * @param userId идентификатор пользователя
     * @return {@code true}, если функция включена в тарифе
     */
    public boolean canUseAutoUpdate(Long userId) {
        return isFeatureEnabled(userId, FeatureKey.AUTO_UPDATE);
    }

    /**
     * Проверяет, разрешены ли Telegram-уведомления для пользователя.
     *
     * @param userId идентификатор пользователя
     * @return {@code true}, если тарифный план пользователя позволяет Telegram-уведомления
     */
    public boolean canUseTelegramNotifications(Long userId) {
        boolean allowed = isFeatureEnabled(userId, FeatureKey.TELEGRAM_NOTIFICATIONS);
        if (!allowed) {
            log.warn("Пользователь {} не имеет активной подписки или функция Telegram недоступна.", userId);
        }
        return allowed;
    }

    /**
     * Проверяет доступность функции для указанного пользователя.
     *
     * @param userId идентификатор пользователя
     * @param key    ключ функции
     * @return {@code true}, если функция доступна
     */
    public boolean isFeatureEnabled(Long userId, FeatureKey key) {
        String code = userSubscriptionRepository.getSubscriptionPlanCode(userId);
        if (code == null) {
            return false;
        }
        return subscriptionPlanRepository.findByCode(code)
                .map(plan -> plan.isFeatureEnabled(key))
                .orElse(false);
    }

    /**
     * Проверяет, использует ли пользователь платный тарифный план.
     *
     * @param userId идентификатор пользователя
     * @return {@code true}, если стоимость текущего плана больше нуля
     */
    public boolean isUserPremium(Long userId) {
        String code = userSubscriptionRepository.getSubscriptionPlanCode(userId);
        if (code == null) {
            return false;
        }
        return subscriptionPlanRepository.findByCode(code)
                .map(SubscriptionPlan::isPaid)
                .orElse(false);
    }

    /**
     * Продлевает платную подписку пользователя или переводит его на неё.
     * <p>
     * Если текущий тариф платный, его срок продлевается.
     * При бесплатном плане выполняется апгрейд на платный тариф.
     * </p>
     *
     * @param userId идентификатор пользователя
     * @param months количество месяцев продления
     * @throws IllegalArgumentException если подписка не найдена либо её тип не поддерживает апгрейд
     * @throws IllegalStateException    если у пользователя отсутствует план подписки
     */
    @Transactional
    public void upgradeOrExtendSubscription(Long userId, int months) {
        log.info("🔄 Попытка обновления подписки для пользователя с ID: {}", userId);

        UserSubscription subscription = userSubscriptionRepository.findByUserId(userId)
                .orElseThrow(() -> {
                    log.warn("⛔ Пользователь с ID {} не имеет активной подписки.", userId);
                    return new IllegalArgumentException("Подписка не найдена");
                });

        ZonedDateTime nowUtc = ZonedDateTime.now(ZoneOffset.UTC);
        SubscriptionPlan plan = subscription.getSubscriptionPlan();

        if (plan == null) {
            log.error("🚨 Ошибка! У пользователя {} отсутствует подписка!", userId);
            throw new IllegalStateException("У пользователя отсутствует подписка");
        }

        if (plan.isPaid()) {
            extendPaidSubscription(subscription, months, nowUtc);
        } else {
            upgradeToPaidSubscription(subscription, months, nowUtc);
        }

        userSubscriptionRepository.save(subscription);
        log.info("✅ Подписка пользователя ID={} успешно обновлена. Новый план: {} до {}",
                userId, subscription.getSubscriptionPlan().getCode(), subscription.getSubscriptionEndDate());
    }

    private void extendPaidSubscription(UserSubscription subscription, int months, ZonedDateTime nowUtc) {
        ZonedDateTime currentExpiry = subscription.getSubscriptionEndDate();
        if (currentExpiry == null || currentExpiry.isBefore(nowUtc)) {
            currentExpiry = nowUtc;
        }
        subscription.setSubscriptionEndDate(currentExpiry.plusMonths(months));
        log.info("🔄 Продление подписки пользователя {} до {}", subscription.getUser().getId(), subscription.getSubscriptionEndDate());
    }

    private void upgradeToPaidSubscription(UserSubscription subscription, int months, ZonedDateTime nowUtc) {
        SubscriptionPlan paidPlan = subscriptionPlanRepository
                .findFirstByMonthlyPriceGreaterThanOrAnnualPriceGreaterThan(BigDecimal.ZERO, BigDecimal.ZERO)
                .orElseThrow(() -> new RuntimeException("🚨 Платный план не найден"));

        subscription.setSubscriptionPlan(paidPlan);
        subscription.setSubscriptionEndDate(nowUtc.plusMonths(months));
        log.info("⬆️ Апгрейд пользователя {} до платной подписки до {}",
                subscription.getUser().getId(), subscription.getSubscriptionEndDate());
    }

    /**
     * Изменяет подписку пользователя на указанный тарифный план.
     * <p>
     * Для платных планов устанавливается срок действия, для бесплатных
     * дата окончания очищается.
     * </p>
     *
     * @param userId      идентификатор пользователя
     * @param code        код нового плана
     * @param months      срок действия в месяцах (применяется для платных планов)
     * @throws IllegalArgumentException если подписка пользователя или новый план не найдены
     */
    @Transactional
    public void changeSubscription(Long userId, String code, Integer months) {
        log.info("Начало смены подписки пользователя ID={} на {}", userId, code);

        // Нормализуем код тарифа
        String parsedCode = parseCode(code)
                .orElseThrow(() -> new IllegalArgumentException("Код плана не задан"));

        UserSubscription subscription = userSubscriptionRepository.findByUserId(userId)
                .orElseThrow(() -> new IllegalArgumentException("Подписка пользователя не найдена"));

        // Получаем план из БД по коду
        SubscriptionPlan newPlan = subscriptionPlanRepository.findByCode(parsedCode)
                .orElseThrow(() -> new IllegalArgumentException("План с кодом " + parsedCode + " не найден"));

        // Устанавливаем новый план
        subscription.setSubscriptionPlan(newPlan);

        if (newPlan.isPaid()) {
            int subscriptionMonths = (months != null && months > 0) ? months : 1;
            subscription.setSubscriptionEndDate(ZonedDateTime.now(ZoneOffset.UTC).plusMonths(subscriptionMonths));
            log.info("⬆️ Пользователь {} получил платную подписку до {}", userId, subscription.getSubscriptionEndDate());
        } else {
            subscription.setSubscriptionEndDate(null);
            log.info("⬇️ Пользователь {} переведен на план {}", userId, parsedCode);
        }

        userSubscriptionRepository.save(subscription);
        log.info("✅ Подписка пользователя с ID {} изменена на {} до {}", userId, parsedCode, subscription.getSubscriptionEndDate());
    }

    /**
     * Создаёт базовую подписку для нового пользователя.
     * <p>
     * Пользователь автоматически привязывается к бесплатному тарифному плану
     * со сброшенными лимитами обновлений.
     * </p>
     *
     * @param user пользователь, которому создаётся подписка
     * @return новая подписка пользователя
     */
    public UserSubscription createDefaultSubscriptionForUser(User user) {
        SubscriptionPlan defaultPlan = subscriptionPlanRepository
                .findByCode("FREE")
                .orElseThrow(() -> new IllegalStateException("Бесплатный план не найден"));

        UserSubscription subscription = new UserSubscription();
        subscription.setUser(user);
        subscription.setSubscriptionPlan(defaultPlan);
        subscription.setResetDate(LocalDate.now());
        subscription.setUpdateCount(0);

        return subscription;
    }

    /**
     * Нормализует код тарифного плана.
     *
     * @param name исходное значение кода
     * @return нормализованный код в верхнем регистре или {@link Optional#empty()} при пустом значении
     */
    private Optional<String> parseCode(String name) {
        if (name == null || name.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(name.trim().toUpperCase());
    }

}
