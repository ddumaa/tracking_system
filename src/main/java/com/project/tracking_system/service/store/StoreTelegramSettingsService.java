package com.project.tracking_system.service.store;

import com.project.tracking_system.dto.StoreTelegramSettingsDTO;
import com.project.tracking_system.entity.*;
import com.project.tracking_system.repository.StoreTelegramSettingsRepository;
import com.project.tracking_system.service.SubscriptionService;
import com.project.tracking_system.controller.WebSocketController;
import com.project.tracking_system.model.subscription.FeatureKey;
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

        if (enableRequested && !subscriptionService.isFeatureEnabled(userId, FeatureKey.TELEGRAM_NOTIFICATIONS)) {
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

        // Обновляем пользовательские шаблоны
        settings.getTemplates().clear();
        if (dto.getTemplates() != null && !dto.getTemplates().isEmpty()) {
            dto.getTemplates().forEach((key, value) -> {
                if (value == null || value.isBlank()) return;
                validateTemplate(value);
                StoreTelegramTemplate tpl = new StoreTelegramTemplate();
                tpl.setStatus(BuyerStatus.valueOf(key));
                tpl.setTemplate(value);
                tpl.setSettings(settings);
                settings.getTemplates().add(tpl);
            });
        }

        settingsRepository.save(settings);
        store.setTelegramSettings(settings);
        log.info("Настройки Telegram для магазина ID={} обновлены", store.getId());
    }

    // Проверяем наличие обязательных плейсхолдеров
    private void validateTemplate(String template) {
        if (!template.contains("{track}") || !template.contains("{store}")) {
            throw new IllegalArgumentException("Шаблон должен содержать {track} и {store}");
        }
    }
}
