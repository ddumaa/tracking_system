package com.project.tracking_system.service.store;

import com.project.tracking_system.dto.StoreTelegramSettingsDTO;
import com.project.tracking_system.entity.Store;
import com.project.tracking_system.entity.StoreTelegramSettings;
import com.project.tracking_system.repository.StoreTelegramSettingsRepository;
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

    /**
     * Создать или обновить настройки Telegram магазина.
     *
     * @param store магазин, к которому относятся настройки
     * @param dto   данные настроек
     */
    @Transactional
    public void update(Store store, StoreTelegramSettingsDTO dto) {
        StoreTelegramSettings settings = settingsRepository.findByStoreId(store.getId());
        if (settings == null) {
            settings = new StoreTelegramSettings();
            settings.setStore(store);
        }
        settings.setEnabled(dto.isEnabled());
        settings.setReminderStartAfterDays(dto.getReminderStartAfterDays());
        settings.setReminderRepeatIntervalDays(dto.getReminderRepeatIntervalDays());
        settings.setCustomSignature(dto.getCustomSignature());
        settingsRepository.save(settings);
        store.setTelegramSettings(settings);
        log.info("Настройки Telegram для магазина ID={} обновлены", store.getId());
    }
}
