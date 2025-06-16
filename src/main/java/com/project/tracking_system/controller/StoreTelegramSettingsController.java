package com.project.tracking_system.controller;

import com.project.tracking_system.dto.StoreTelegramSettingsDTO;
import com.project.tracking_system.entity.Store;
import com.project.tracking_system.entity.StoreTelegramSettings;
import com.project.tracking_system.service.store.StoreService;
import com.project.tracking_system.repository.StoreTelegramSettingsRepository;
import com.project.tracking_system.service.store.StoreTelegramSettingsService;
import com.project.tracking_system.utils.ResponseBuilder;
import com.project.tracking_system.utils.AuthUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import java.security.Principal;

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
    private final StoreTelegramSettingsService telegramSettingsService;

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
    @PostMapping(consumes = org.springframework.http.MediaType.APPLICATION_JSON_VALUE)
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

    /**
     * Обновляет настройки через форму профиля.
     *
     * @param storeId            идентификатор магазина
     * @param dto                заполненные настройки Telegram
     * @param principal          текущий аутентифицированный пользователь
     * @param redirectAttributes атрибуты для передачи уведомления об успехе
     * @return редирект на страницу профиля пользователя
     */
    @PostMapping(consumes = org.springframework.http.MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public String updateSettingsForm(@PathVariable("storeId") Long storeId,
                                     @ModelAttribute StoreTelegramSettingsDTO dto,
                                     Principal principal,
                                     RedirectAttributes redirectAttributes) {
        Store store = storeService.findOwnedByUser(storeId, principal);
        telegramSettingsService.update(store, dto);
        redirectAttributes.addFlashAttribute("successMessage", "Настройки Telegram сохранены.");
        return "redirect:/profile#v-pills-stores";
    }
}
