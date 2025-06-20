package com.project.tracking_system.controller;

import com.project.tracking_system.dto.StoreTelegramSettingsDTO;
import com.project.tracking_system.entity.Store;
import com.project.tracking_system.entity.StoreTelegramSettings;
import com.project.tracking_system.service.store.StoreService;
import com.project.tracking_system.repository.StoreTelegramSettingsRepository;
import com.project.tracking_system.service.store.StoreTelegramSettingsService;
import com.project.tracking_system.utils.ResponseBuilder;
import com.project.tracking_system.utils.AuthUtils;
import com.project.tracking_system.controller.WebSocketController;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.validation.BindingResult;

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
    private final WebSocketController webSocketController;

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
     * Обновить настройки магазина (JSON).
     */
    @PostMapping(consumes = org.springframework.http.MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<?> updateSettings(@PathVariable Long storeId,
                                            @Valid @RequestBody StoreTelegramSettingsDTO dto,
                                            BindingResult binding,
                                            Authentication authentication) {
        Long userId = AuthUtils.getCurrentUser(authentication).getId();
        try {
            if (binding.hasErrors()) {
                return ResponseBuilder.error(HttpStatus.BAD_REQUEST,
                        binding.getAllErrors().get(0).getDefaultMessage());
            }

            Store store = storeService.getStore(storeId, userId);
            StoreTelegramSettings settings = settingsRepository.findByStoreId(store.getId());
            storeService.updateFromDto(settings, dto);
            settingsRepository.save(settings);
            webSocketController.sendUpdateStatus(userId, "Настройки Telegram сохранены.", true);
            return ResponseBuilder.ok(storeService.toDto(settings));
        } catch (Exception e) {
            log.error("Ошибка обновления настроек Telegram", e);
            return ResponseBuilder.error(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    /**
     * Обновить настройки магазина через AJAX-форму.
     * <p>
     * Метод используется при отправке формы без перезагрузки страницы и
     * отправляет уведомление через WebSocket.
     *
     * @param storeId        идентификатор магазина
     * @param dto            заполненные настройки Telegram
     * @param binding        результаты валидации формы
     * @param authentication текущая аутентификация пользователя
     * @return {@link ResponseEntity} с HTTP 200 или ошибкой в тексте
     */
    @PostMapping(consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseBody
    public ResponseEntity<?> updateSettingsAjax(@PathVariable Long storeId,
                                                @Valid @ModelAttribute StoreTelegramSettingsDTO dto,
                                                BindingResult binding,
                                                Authentication authentication) {
        Long userId = AuthUtils.getCurrentUser(authentication).getId();
        if (binding.hasErrors()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(binding.getAllErrors().get(0).getDefaultMessage());
        }

        Store store = storeService.getStore(storeId, userId);
        telegramSettingsService.update(store, dto);
        // Отправляем уведомление через WebSocket
        webSocketController.sendUpdateStatus(userId, "Настройки Telegram сохранены.", true);
        return ResponseEntity.ok().build();
    }

    /**
     * Обновляет настройки через форму профиля.
     *
     * @param storeId            идентификатор магазина
     * @param dto                заполненные настройки Telegram
     * @param authentication     текущая аутентификация пользователя
     * @param redirectAttributes атрибуты для передачи уведомления об успехе
     * @return редирект на страницу профиля пользователя
     */
    @PostMapping(consumes = org.springframework.http.MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public String updateSettingsForm(@PathVariable("storeId") Long storeId,
                                     @Valid @ModelAttribute StoreTelegramSettingsDTO dto,
                                     BindingResult binding,
                                     Authentication authentication,
                                     RedirectAttributes redirectAttributes) {
        Long userId = AuthUtils.getCurrentUser(authentication).getId();
        if (binding.hasErrors()) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    binding.getAllErrors().get(0).getDefaultMessage());
            return "redirect:/profile#v-pills-stores";
        }
        Store store = storeService.getStore(storeId, userId);
        telegramSettingsService.update(store, dto);
        redirectAttributes.addFlashAttribute("successMessage", "Настройки Telegram сохранены.");
        webSocketController.sendUpdateStatus(userId, "Настройки Telegram сохранены.", true);
        return "redirect:/profile#v-pills-stores";
    }
}
