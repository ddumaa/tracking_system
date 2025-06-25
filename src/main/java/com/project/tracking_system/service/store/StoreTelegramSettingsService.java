package com.project.tracking_system.service.store;

import com.project.tracking_system.dto.StoreTelegramSettingsDTO;
import com.project.tracking_system.entity.*;
import com.project.tracking_system.repository.StoreTelegramSettingsRepository;
import com.project.tracking_system.service.SubscriptionService;
import com.project.tracking_system.controller.WebSocketController;
import com.project.tracking_system.exception.InvalidTemplateException;
import com.project.tracking_system.model.subscription.FeatureKey;
import com.project.tracking_system.service.store.StoreService;
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
    private final StoreService storeService;

    /**
     * Создать или обновить настройки Telegram магазина.
     * <p>
     * Если владелец магазина имеет план {@code FREE}, включение Telegram-уведомлений запрещено.
     * Пользователь получит предупреждение через WebSocket, а настройки не будут сохранены.
     * </p>
     * Флаг {@code useCustomTemplates} определяет, сохранять ли переданные шаблоны.
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

        // Проверяем содержимое пользовательских шаблонов при их использовании
        if (dto.isUseCustomTemplates() && dto.getTemplates() != null) {
            dto.getTemplates().values().forEach(this::validateTemplate);
        }

        // Передаём обработку полей общему сервису
        storeService.updateFromDto(settings, dto);

        settingsRepository.save(settings);
        store.setTelegramSettings(settings);
        log.info("Настройки Telegram для магазина ID={} обновлены", store.getId());
    }

    // Проверяем наличие обязательных плейсхолдеров
    private void validateTemplate(String template) {
        if (!template.contains("{track}") || !template.contains("{store}")) {
            throw new InvalidTemplateException("Шаблон должен содержать {track} и {store}");
        }
    }
}
