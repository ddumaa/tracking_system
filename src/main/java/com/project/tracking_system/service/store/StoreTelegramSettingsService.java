package com.project.tracking_system.service.store;

import com.project.tracking_system.dto.StoreTelegramSettingsDTO;
import com.project.tracking_system.entity.Store;
import com.project.tracking_system.entity.StoreTelegramSettings;
import com.project.tracking_system.repository.StoreTelegramSettingsRepository;
import com.project.tracking_system.service.SubscriptionService;
import com.project.tracking_system.controller.WebSocketController;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Сервис управления Telegram-настройками магазина.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class StoreTelegramSettingsService {

    private final StoreTelegramSettingsRepository settingsRepository;
    private final SubscriptionService subscriptionService;
    private final WebSocketController webSocketController;

    /**
     * Создать или обновить настройки Telegram магазина.
     * <p>
     * Если владелец магазина имеет план {@code FREE}, включение Telegram-уведомлений запрещено.
     * Пользователь получит предупреждение через WebSocket, а настройки не будут сохранены.
     * </p>
     *
     * @param store  магазин, к которому относятся настройки
     * @param dto    данные настроек
     * @param userId идентификатор владельца магазина
     * @throws IllegalStateException если попытка включения уведомлений при бесплатном плане
     */
    @Transactional
    public void update(Store store, StoreTelegramSettingsDTO dto, Long userId) {
        boolean enableRequested = dto.isEnabled();

        if (enableRequested && !subscriptionService.isFeatureEnabled(userId, "telegramNotifications")) {
            String msg = "Telegram-уведомления недоступны на вашем тарифе.";
            webSocketController.sendUpdateStatus(userId, msg, false);
            log.warn("⛔ Попытка включить Telegram-уведомления магазином ID={} без премиум-подписки", store.getId());
            throw new IllegalStateException(msg);
        }

        StoreTelegramSettings settings = settingsRepository.findByStoreId(store.getId());
        if (settings == null) {
            settings = new StoreTelegramSettings();
            settings.setStore(store);
        }

        settings.setEnabled(dto.isEnabled());
        settings.setReminderStartAfterDays(dto.getReminderStartAfterDays());
        settings.setReminderRepeatIntervalDays(dto.getReminderRepeatIntervalDays());
        settings.setCustomSignature(dto.getCustomSignature());
        settings.setRemindersEnabled(dto.isRemindersEnabled());
        settingsRepository.save(settings);
        store.setTelegramSettings(settings);
        log.info("Настройки Telegram для магазина ID={} обновлены", store.getId());
    }
}
