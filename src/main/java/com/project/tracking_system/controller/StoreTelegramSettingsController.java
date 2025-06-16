package com.project.tracking_system.controller;

import com.project.tracking_system.dto.StoreTelegramSettingsDTO;
import com.project.tracking_system.entity.Store;
import com.project.tracking_system.entity.StoreTelegramSettings;
import com.project.tracking_system.service.store.StoreService;
import com.project.tracking_system.repository.StoreTelegramSettingsRepository;
import com.project.tracking_system.utils.ResponseBuilder;
import com.project.tracking_system.utils.AuthUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

/**
 * Управление Telegram-настройками магазина.
 */
@Controller
@RequiredArgsConstructor
@Slf4j
@RequestMapping("/stores/{storeId}/telegram-settings")
public class StoreTelegramSettingsController {

    private final StoreService storeService;
    private final StoreTelegramSettingsRepository settingsRepository;

    /**
     * Получить текущие настройки магазина.
     */
    @GetMapping
    @ResponseBody
    public ResponseEntity<?> getSettings(@PathVariable Long storeId, Authentication authentication) {
        Long userId = AuthUtils.getCurrentUser(authentication).getId();
        Store store = storeService.getStore(storeId, userId);
        StoreTelegramSettings settings = settingsRepository.findByStoreId(store.getId());
        return ResponseBuilder.ok(storeService.toDto(settings));
    }

    /**
     * Обновить настройки магазина.
     */
    @PostMapping
    @ResponseBody
    public ResponseEntity<?> updateSettings(@PathVariable Long storeId,
                                            @RequestBody StoreTelegramSettingsDTO dto,
                                            Authentication authentication) {
        Long userId = AuthUtils.getCurrentUser(authentication).getId();
        try {
            Store store = storeService.getStore(storeId, userId);
            StoreTelegramSettings settings = settingsRepository.findByStoreId(store.getId());
            if (dto.getReminderStartAfterDays() < 1 || dto.getReminderStartAfterDays() > 14
                    || dto.getReminderRepeatIntervalDays() < 1 || dto.getReminderRepeatIntervalDays() > 14) {
                return ResponseBuilder.error(HttpStatus.BAD_REQUEST, "Диапазон дней 1-14");
            }
            storeService.updateFromDto(settings, dto);
            settingsRepository.save(settings);
            return ResponseBuilder.ok(storeService.toDto(settings));
        } catch (Exception e) {
            log.error("Ошибка обновления настроек Telegram", e);
            return ResponseBuilder.error(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }
}
