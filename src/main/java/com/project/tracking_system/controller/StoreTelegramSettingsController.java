package com.project.tracking_system.controller;

import com.project.tracking_system.dto.StoreTelegramSettingsDTO;
import com.project.tracking_system.entity.Store;
import com.project.tracking_system.entity.StoreTelegramSettings;
import com.project.tracking_system.entity.User;
import com.project.tracking_system.service.store.StoreService;
import com.project.tracking_system.repository.StoreTelegramSettingsRepository;
import com.project.tracking_system.service.store.StoreTelegramSettingsService;
import com.project.tracking_system.service.SubscriptionService;
import com.project.tracking_system.exception.InvalidTemplateException;
import com.project.tracking_system.utils.ResponseBuilder;
import com.project.tracking_system.controller.WebSocketController;
import com.project.tracking_system.dto.CustomerTelegramLinkDTO;
import com.project.tracking_system.service.customer.CustomerTelegramService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.validation.BindingResult;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

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
    private final SubscriptionService subscriptionService;
    private final CustomerTelegramService customerTelegramService;

    /**
     * Получить текущие настройки магазина.
     * <p>
     * Метод возвращает параметры Telegram для указанного магазина.
     *
     * @param storeId идентификатор магазина
     * @param user    текущий пользователь
     * @return {@link ResponseEntity} с {@link StoreTelegramSettingsDTO} в теле
     */
    @GetMapping
    @ResponseBody
    public ResponseEntity<?> getSettings(@PathVariable Long storeId, @AuthenticationPrincipal User user) {
        Long userId = user.getId();
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
                                            @AuthenticationPrincipal User user) {
        Long userId = user.getId();
        try {
            if (binding.hasErrors()) {
                return ResponseBuilder.error(HttpStatus.BAD_REQUEST,
                        binding.getAllErrors().get(0).getDefaultMessage());
            }

            Store store = storeService.getStore(storeId, userId);
            telegramSettingsService.update(store, dto, userId);
            webSocketController.sendUpdateStatus(userId, "Настройки Telegram сохранены.", true);
            return ResponseBuilder.ok(storeService.toDto(store.getTelegramSettings()));
        } catch (InvalidTemplateException e) {
            log.warn("Некорректный шаблон Telegram: {}", e.getMessage());
            return ResponseBuilder.error(HttpStatus.BAD_REQUEST, e.getMessage());
        } catch (IllegalStateException e) {
            log.warn("Ошибка обновления настроек Telegram: {}", e.getMessage());
            return ResponseBuilder.error(HttpStatus.FORBIDDEN, e.getMessage());
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
     * @param user           текущий пользователь
     * @return {@link ResponseEntity} с HTTP 200 или ошибкой в тексте
     */
    @PostMapping(consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseBody
    public ResponseEntity<?> updateSettingsAjax(@PathVariable Long storeId,
                                                @Valid @ModelAttribute StoreTelegramSettingsDTO dto,
                                                BindingResult binding,
                                                @AuthenticationPrincipal User user) {
        Long userId = user.getId();
        if (binding.hasErrors()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(binding.getAllErrors().get(0).getDefaultMessage());
        }

        Store store = storeService.getStore(storeId, userId);
        try {
            telegramSettingsService.update(store, dto, userId);
            webSocketController.sendUpdateStatus(userId, "Настройки Telegram сохранены.", true);
            return ResponseEntity.ok().build();
        } catch (InvalidTemplateException e) {
            log.warn("Некорректный шаблон Telegram: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (IllegalStateException e) {
            log.warn("Ошибка обновления настроек Telegram: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage());
        }
    }

    /**
     * Обновляет настройки через форму профиля.
     *
     * @param storeId            идентификатор магазина
     * @param dto                заполненные настройки Telegram
     * @param user                текущий пользователь
     * @param redirectAttributes  атрибуты для передачи уведомления об успехе
     * @return редирект на страницу профиля пользователя
     */
    @PostMapping(consumes = org.springframework.http.MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public String updateSettingsForm(@PathVariable("storeId") Long storeId,
                                     @Valid @ModelAttribute StoreTelegramSettingsDTO dto,
                                     BindingResult binding,
                                     @AuthenticationPrincipal User user,
                                     RedirectAttributes redirectAttributes) {
        Long userId = user.getId();
        if (binding.hasErrors()) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    binding.getAllErrors().get(0).getDefaultMessage());
            return "redirect:/profile#v-pills-stores";
        }
        Store store = storeService.getStore(storeId, userId);
        try {
            telegramSettingsService.update(store, dto, userId);
            redirectAttributes.addFlashAttribute("successMessage", "Настройки Telegram сохранены.");
            webSocketController.sendUpdateStatus(userId, "Настройки Telegram сохранены.", true);
        } catch (InvalidTemplateException e) {
            log.warn("Некорректный шаблон Telegram: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("templateError", e.getMessage());
            redirectAttributes.addFlashAttribute("storeIdWithError", storeId);
        } catch (IllegalStateException e) {
            log.warn("Ошибка обновления настроек Telegram: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/profile#v-pills-stores";
    }

    /**
     * Привязывает к магазину собственного Telegram-бота.
     *
     * @param storeId  идентификатор магазина
     * @param botToken токен бота
     * @param user     текущий пользователь
     * @return обновлённые настройки Telegram
     */
    @PostMapping("/custom-bot")
    @ResponseBody
    public ResponseEntity<?> addCustomBot(@PathVariable Long storeId,
                                          @RequestParam String botToken,
                                          @AuthenticationPrincipal User user) {
        Long userId = user.getId();
        try {
            Store store = storeService.getStore(storeId, userId);
            telegramSettingsService.connectCustomBot(store, botToken, userId);
            webSocketController.sendUpdateStatus(userId, "Бот сохранён", true);
            return ResponseBuilder.ok(storeService.toDto(store.getTelegramSettings()));
        } catch (IllegalStateException e) {
            log.warn("Ошибка сохранения бота: {}", e.getMessage());
            return ResponseBuilder.error(HttpStatus.FORBIDDEN, e.getMessage());
        } catch (Exception e) {
            log.error("Ошибка валидации бота", e);
            return ResponseBuilder.error(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    /**
     * Удаляет токен собственного бота из настроек магазина.
     *
     * @param storeId идентификатор магазина
     * @param user    текущий пользователь
     * @return статус выполнения операции
     */
    @PostMapping("/delete-custom-bot")
    @ResponseBody
    public ResponseEntity<?> deleteCustomBot(@PathVariable Long storeId,
                                             @AuthenticationPrincipal User user) {
        Long userId = user.getId();
        try {
            Store store = storeService.getStore(storeId, userId);
            telegramSettingsService.removeCustomBot(store);
            webSocketController.sendUpdateStatus(userId, "Бот удалён", true);
            return ResponseBuilder.ok(storeService.toDto(store.getTelegramSettings()));
        } catch (IllegalStateException e) {
            log.warn("Ошибка удаления бота: {}", e.getMessage());
            return ResponseBuilder.error(HttpStatus.FORBIDDEN, e.getMessage());
        }
    }

    /**
     * Возвращает список Telegram-подписчиков магазина.
     *
     * @param storeId идентификатор магазина
     * @param user    текущий пользователь
     * @return список привязок в виде DTO
     */
    @GetMapping("/links")
    @ResponseBody
    public List<CustomerTelegramLinkDTO> getCustomerLinks(@PathVariable Long storeId,
                                                          @AuthenticationPrincipal User user) {
        Long userId = user.getId();
        storeService.getStore(storeId, userId); // проверяем права
        return customerTelegramService.getLinksByStore(storeId);
    }

    /**
     * Изменяет состояние уведомлений для привязки.
     *
     * @param storeId идентификатор магазина
     * @param linkId  идентификатор привязки
     * @param enabled новое состояние уведомлений
     * @param user    текущий пользователь
     * @return результат операции
     */
    @PostMapping("/links/{linkId}/notifications")
    @ResponseBody
    public ResponseEntity<?> toggleNotifications(@PathVariable Long storeId,
                                                 @PathVariable Long linkId,
                                                 @RequestParam boolean enabled,
                                                 @AuthenticationPrincipal User user) {
        Long userId = user.getId();
        storeService.getStore(storeId, userId); // проверка владельца
        boolean result = customerTelegramService.setNotificationsEnabled(linkId, storeId, enabled);
        if (result) {
            return ResponseBuilder.ok(null);
        }
        return ResponseBuilder.error(HttpStatus.BAD_REQUEST, "Привязка не найдена");
    }

}
