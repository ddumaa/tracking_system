package com.project.tracking_system.service;

import com.project.tracking_system.entity.SubscriptionCode;
import com.project.tracking_system.entity.SubscriptionPlan;
import com.project.tracking_system.entity.User;
import com.project.tracking_system.entity.UserSubscription;
import com.project.tracking_system.repository.SubscriptionPlanRepository;
import com.project.tracking_system.repository.TrackParcelRepository;
import com.project.tracking_system.repository.UserSubscriptionRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Optional;

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

    private static final SubscriptionCode PREMIUM_PLAN = SubscriptionCode.PREMIUM;
    private static final SubscriptionCode FREE_PLAN = SubscriptionCode.FREE;

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

        // Получаем лимит треков на файл
        Integer maxTracksPerFile = (plan != null) ? plan.getMaxTracksPerFile() : null;
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

        Integer maxSavedTracks = (plan != null) ? plan.getMaxSavedTracks() : null;
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
        Integer maxUpdates = (plan != null) ? plan.getMaxTrackUpdates() : null;
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
     * @return {@code true}, если пользователь имеет премиум-подписку
     */
    public boolean canUseBulkUpdate(Long userId) {
        SubscriptionCode code = userSubscriptionRepository.getSubscriptionPlanCode(userId);

        if (code == null) {
            log.warn("Пользователь {} не имеет активной подписки. Массовое обновление недоступно.", userId);
            return false;
        }

        boolean hasAccess = (code == SubscriptionCode.PREMIUM);
        log.debug("Пользователь {} пытается использовать массовое обновление. Доступ: {}", userId, hasAccess);
        return hasAccess;
    }

    /**
     * Проверяет, разрешены ли Telegram-уведомления для пользователя.
     *
     * @param userId идентификатор пользователя
     * @return {@code true}, если тарифный план пользователя позволяет Telegram-уведомления
     */
    public boolean canUseTelegramNotifications(Long userId) {
        SubscriptionCode code = userSubscriptionRepository.getSubscriptionPlanCode(userId);
        if (code == null) {
            log.warn("Пользователь {} не имеет активной подписки. Telegram недоступен.", userId);
            return false;
        }
        return subscriptionPlanRepository.findByCode(code)
                .map(SubscriptionPlan::getAllowTelegramNotifications)
                .orElse(false);
    }

    /**
     * Проверяет наличие подписки PREMIUM у пользователя.
     *
     * @param userId идентификатор пользователя
     * @return {@code true}, если у пользователя активен премиум-план
     */
    public boolean isUserPremium(Long userId) {
        SubscriptionCode code = userSubscriptionRepository.getSubscriptionPlanCode(userId);
        return code == SubscriptionCode.PREMIUM;
    }

    /**
     * Продлевает текущую подписку пользователя либо переводит его на PREMIUM.
     * <p>
     * Если пользователь уже имеет PREMIUM-подписку, срок продлевается.
     * При наличии бесплатного плана происходит апгрейд на PREMIUM.
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

        if (PREMIUM_PLAN.equals(plan.getCode())) {
            extendPremiumSubscription(subscription, months, nowUtc);
        } else if (FREE_PLAN.equals(plan.getCode())) {
            upgradeToPremiumSubscription(subscription, months, nowUtc);
        } else {
            log.warn("⚠️ Попытка обновления подписки пользователя {}, но его статус не позволяет это сделать", userId);
            throw new IllegalArgumentException("Пользователь в статусе, который нельзя апгрейдить");
        }

        userSubscriptionRepository.save(subscription);
        log.info("✅ Подписка пользователя ID={} успешно обновлена. Новый план: {} до {}",
                userId, subscription.getSubscriptionPlan().getCode(), subscription.getSubscriptionEndDate());
    }

    private void extendPremiumSubscription(UserSubscription subscription, int months, ZonedDateTime nowUtc) {
        ZonedDateTime currentExpiry = subscription.getSubscriptionEndDate();
        if (currentExpiry == null || currentExpiry.isBefore(nowUtc)) {
            currentExpiry = nowUtc;
        }
        subscription.setSubscriptionEndDate(currentExpiry.plusMonths(months));
        log.info("🔄 Продление подписки пользователя {} до {}", subscription.getUser().getId(), subscription.getSubscriptionEndDate());
    }

    private void upgradeToPremiumSubscription(UserSubscription subscription, int months, ZonedDateTime nowUtc) {
        SubscriptionPlan premiumPlan = subscriptionPlanRepository.findByCode(PREMIUM_PLAN)
                .orElseThrow(() -> new RuntimeException("🚨 План " + PREMIUM_PLAN + " не найден"));

        subscription.setSubscriptionPlan(premiumPlan);
        subscription.setSubscriptionEndDate(nowUtc.plusMonths(months));

        log.info("⬆️ Апгрейд пользователя {} до подписки {} с подпиской до {}",
                subscription.getUser().getId(), PREMIUM_PLAN, subscription.getSubscriptionEndDate());
    }

    /**
     * Изменяет подписку пользователя на указанный тариф.
     * <p>
     * При переходе на PREMIUM устанавливается срок действия. Для бесплатного
     * плана срок обнуляется.
     * </p>
     *
     * @param userId      идентификатор пользователя
     * @param newPlanName название нового плана
     * @param months      срок действия в месяцах (только для PREMIUM; может быть {@code null})
     * @throws IllegalArgumentException если подписка пользователя или новый план не найдены
     */
    @Transactional
    public void changeSubscription(Long userId, SubscriptionCode code, Integer months) {
        log.info("Начало смены подписки пользователя ID={} на {}", userId, code);

        UserSubscription subscription = userSubscriptionRepository.findByUserId(userId)
                .orElseThrow(() -> new IllegalArgumentException("Подписка пользователя не найдена"));

        // Получаем план из БД по enum-коду
        SubscriptionPlan newPlan = subscriptionPlanRepository.findByCode(code)
                .orElseThrow(() -> new IllegalArgumentException("План с кодом " + code + " не найден"));

        // Устанавливаем новый план
        subscription.setSubscriptionPlan(newPlan);

        if (code == SubscriptionCode.PREMIUM) {
            int subscriptionMonths = (months != null && months > 0) ? months : 1;
            subscription.setSubscriptionEndDate(ZonedDateTime.now(ZoneOffset.UTC).plusMonths(subscriptionMonths));
            log.info("⬆️ Пользователь {} получил подписку {} до {}", userId, code, subscription.getSubscriptionEndDate());
        } else {
            subscription.setSubscriptionEndDate(null);
            log.info("⬇️ Пользователь {} переведен на план {}", userId, code);
        }

        userSubscriptionRepository.save(subscription);
        log.info("✅ Подписка пользователя с ID {} изменена на {} до {}", userId, code, subscription.getSubscriptionEndDate());
    }

    public UserSubscription createDefaultSubscriptionForUser(User user) {
        SubscriptionPlan defaultPlan = subscriptionPlanRepository.findByCode(SubscriptionCode.FREE)
                .orElseThrow(() -> new IllegalStateException("FREE план не найден"));

        UserSubscription subscription = new UserSubscription();
        subscription.setUser(user);
        subscription.setSubscriptionPlan(defaultPlan);
        subscription.setResetDate(LocalDate.now());
        subscription.setUpdateCount(0);

        return subscription;
    }

    public Optional<SubscriptionCode> parseCode(String name) {
        try {
            return Optional.of(SubscriptionCode.valueOf(name.toUpperCase()));
        } catch (IllegalArgumentException | NullPointerException e) {
            return Optional.empty();
        }
    }

}
