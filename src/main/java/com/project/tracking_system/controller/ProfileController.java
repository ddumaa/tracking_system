package com.project.tracking_system.controller;

import com.project.tracking_system.dto.EvropostCredentialsDTO;
import com.project.tracking_system.dto.UserSettingsDTO;
import com.project.tracking_system.dto.PasswordChangeDTO;
import com.project.tracking_system.entity.Store;
import com.project.tracking_system.dto.StoreDTO;
import com.project.tracking_system.entity.User;
import com.project.tracking_system.service.store.StoreService;
import com.project.tracking_system.service.user.UserService;
import com.project.tracking_system.service.SubscriptionService;
import com.project.tracking_system.model.subscription.FeatureKey;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import com.project.tracking_system.utils.ResponseBuilder;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;


/**
 * Контроллер для управления профилем пользователя.
 * <p>
 * Этот контроллер предоставляет методы для отображения профиля пользователя, настройки учетной записи,
 * изменения пароля и удаления учетной записи пользователя. Все действия выполняются с учетом текущей аутентификации.
 * </p>
 *
 * @author Dmitriy Anisimov
 * @date 07.01.2025
 */
@Controller
@RequiredArgsConstructor
@Slf4j
@RequestMapping("/profile")
public class ProfileController {

    private final UserService userService;
    private final StoreService storeService;
    private final WebSocketController webSocketController;
    private final SubscriptionService subscriptionService;

    /**
     * Отображает страницу профиля пользователя.
     * <p>
     * Этот метод извлекает информацию о текущем пользователе (по имени пользователя из контекста аутентификации)
     * и передает её в модель для отображения на странице.
     * </p>
     *
     * @param model модель для добавления данных в представление
     * @return имя представления страницы профиля
     */
    @GetMapping
    public String profile(Model model, @AuthenticationPrincipal User user) {
        Long userId = user.getId();
        log.info("Получен запрос на отображение профиля для пользователя с ID: {}", userId);

        String storeLimit = userService.getUserStoreLimit(userId);

        // Получаем информацию о профиле пользователя
        var userProfile = userService.getUserProfile(userId);

        // Загружаем магазины с настройками Telegram
        List<StoreDTO> stores = storeService.getUserStoresDto(userId);

        // Добавляем данные профиля в модель
        model.addAttribute("username", user.getEmail());
        model.addAttribute("userProfile", userProfile);
        model.addAttribute("planDetails", userProfile.getPlanDetails());
        model.addAttribute("storeLimit", storeLimit);
        model.addAttribute("stores", stores);
        log.debug("Данные профиля добавлены в модель для пользователя с ID: {}", userId);

        // Добавляем настройки и другие данные пользователя в модель
        UserSettingsDTO settingsDTO = new UserSettingsDTO();
        settingsDTO.setShowBulkUpdateButton(userService.isShowBulkUpdateButton(userId));
        settingsDTO.setTelegramNotificationsEnabled(userService.isTelegramNotificationsEnabled(userId));
        model.addAttribute("userSettingsDTO", settingsDTO);
        model.addAttribute("passwordChangeDTO", new PasswordChangeDTO());
        model.addAttribute("allowCustomTemplates", subscriptionService.canUseCustomNotifications(userId));
        model.addAttribute("defaultReminderTemplate", com.project.tracking_system.service.telegram.TelegramNotificationService.DEFAULT_REMINDER_TEMPLATE);
        model.addAttribute("evropostCredentialsDTO", userService.getEvropostCredentials(userId));

        return "app/profile";
    }

    /**
     * Отображает форму настроек пользователя.
     * <p>
     * При передаче параметра {@code tab} можно открыть вкладки:
     * {@code password} (по умолчанию), {@code evropost}, {@code notifications} и {@code automation}.
     * Метод загружает необходимые данные в модель в зависимости от выбранной вкладки.
     * </p>
     *
     * @param tab    название вкладки
     * @param model  модель для добавления данных в представление
     * @param authentication текущая аутентификация
     * @return имя представления для страницы профиля
     */
    @GetMapping("/settings")
    public String settings(
            @RequestParam(value = "tab", required = false, defaultValue = "password") String tab,
            Model model,
            @AuthenticationPrincipal User user) {

        Long userId = user.getId();
        UserSettingsDTO settingsDTO = new UserSettingsDTO();
        settingsDTO.setShowBulkUpdateButton(userService.isShowBulkUpdateButton(userId));
        settingsDTO.setTelegramNotificationsEnabled(userService.isTelegramNotificationsEnabled(userId));
        model.addAttribute("userSettingsDTO", settingsDTO);
        model.addAttribute("passwordChangeDTO", new PasswordChangeDTO());
        model.addAttribute("allowCustomTemplates", subscriptionService.canUseCustomNotifications(userId));
        model.addAttribute("defaultReminderTemplate", com.project.tracking_system.service.telegram.TelegramNotificationService.DEFAULT_REMINDER_TEMPLATE);

        switch (tab) {
            case "evropost" -> {
                model.addAttribute("evropostCredentialsDTO", userService.getEvropostCredentials(userId));
                log.debug("Форма Европочты подготовлена для пользователя с ID: {}", userId);
            }
            case "notifications" -> {
                List<Store> stores = storeService.getUserStoresWithSettings(userId);
                model.addAttribute("stores", stores);
                log.debug("Вкладка уведомлений открыта, загружено {} магазинов", stores.size());
            }
            case "automation" -> {
                var userProfile = userService.getUserProfile(userId);
                model.addAttribute("userProfile", userProfile);
                model.addAttribute("planDetails", userProfile.getPlanDetails());
                log.debug("Вкладка автоматизации открыта для пользователя с ID: {}", userId);
            }
            default -> log.debug("Открыта вкладка по умолчанию (пароль) для пользователя с ID: {}", userId);
        }

        return "app/profile";
    }

    /**
     * Обновляет данные доступа к API Европочты и связанные настройки.
     *
     * @param evropostCredentialsDTO новые учётные данные Европочты
     * @param bindingResult           результат валидации формы
     * @param model                   модель для передачи данных во фрагмент
     * @param user                    текущий пользователь
     * @return HTML-фрагмент блока Европочты
     */
    @PostMapping("/settings/evropost")
    public String updateEvropostCredentials(
            @Valid @ModelAttribute("evropostCredentialsDTO") EvropostCredentialsDTO evropostCredentialsDTO,
            BindingResult bindingResult, Model model, @AuthenticationPrincipal User user) {

        Long userId = user.getId();

        if (bindingResult.hasErrors()) {
            model.addAttribute("evropostCredentialsDTO", evropostCredentialsDTO);
        } else {
            try {
                userService.updateEvropostCredentialsAndSettings(userId, evropostCredentialsDTO);
                model.addAttribute("evropostCredentialsDTO", userService.getEvropostCredentials(userId));
                webSocketController.sendUpdateStatus(userId, "Данные API Европочты успешно обновлены!", true);
            } catch (Exception e) {
                log.error("Ошибка при обновлении данных Европочты для пользователя с ID {}: {}", userId, e.getMessage(), e);
                model.addAttribute("error", "Ошибка при обновлении данных: " + e.getMessage());
                webSocketController.sendUpdateStatus(userId, "Ошибка обновления Европочты!", false);
            }
        }

        return "app/profile :: evropostFragment";
    }

    /**
     * Обновляет флаг использования собственных учетных данных Европочты.
     *
     * @param useCustomCredentials новое значение флага
     * @param user                 текущий пользователь
     * @return результат операции
     */
    @PostMapping("/settings/use-custom-credentials")
    public ResponseEntity<?> updateUseCustomCredentials(
            @RequestParam(value = "useCustomCredentials", required = false) Boolean useCustomCredentials,
            @AuthenticationPrincipal User user) {

        Long userId = user.getId();

        if (useCustomCredentials == null) {
            return ResponseBuilder.error(HttpStatus.BAD_REQUEST, "Не указан параметр useCustomCredentials");
        }

        try {
            userService.updateUseCustomCredentials(userId, useCustomCredentials);
            return ResponseBuilder.ok("Настройки успешно обновлены.");
        } catch (Exception e) {
            log.error("Ошибка при обновлении настройки для пользователя с ID {}: {}", userId, e.getMessage(), e);
            return ResponseBuilder.error(HttpStatus.INTERNAL_SERVER_ERROR, "Ошибка при обновлении настроек.");
        }
    }

    /**
     * Изменяет настройку автообновления треков пользователя.
     *
     * @param enabled новое значение флага
     * @param user    текущий пользователь
     * @return результат операции
     */
    @PostMapping("/settings/auto-update")
    public ResponseEntity<?> updateAutoUpdate(
            @RequestParam(value = "enabled", required = false) Boolean enabled,
            @AuthenticationPrincipal User user) {

        Long userId = user.getId();

        if (enabled == null) {
            return ResponseBuilder.error(HttpStatus.BAD_REQUEST, "Не указан параметр enabled");
        }

        try {
            // проверяем доступность функции автообновления
            if (!subscriptionService.canUseAutoUpdate(userId)) {
                return ResponseBuilder.error(HttpStatus.FORBIDDEN,
                        "Опция автообновления недоступна на текущем тарифе");
            }
            userService.updateAutoUpdateEnabled(userId, enabled);
            return ResponseBuilder.ok("Настройки успешно обновлены.");
        } catch (Exception e) {
            log.error("Ошибка обновления автообновления для пользователя {}: {}", userId, e.getMessage(), e);
            return ResponseBuilder.error(HttpStatus.INTERNAL_SERVER_ERROR, "Ошибка при обновлении настроек.");
        }
    }

    /**
     * Обновляет настройку отображения кнопки массового обновления.
     *
     * @param show новое значение флага
     * @param user текущий пользователь
     * @return результат операции
     */
    @PostMapping("/settings/bulk-button")
    public ResponseEntity<?> updateBulkButton(
            @RequestParam(value = "show", required = false) Boolean show,
            @AuthenticationPrincipal User user) {
        Long userId = user.getId();

        if (show == null) {
            return ResponseBuilder.error(HttpStatus.BAD_REQUEST, "Не указан параметр show");
        }

        if (!subscriptionService.isFeatureEnabled(userId, FeatureKey.BULK_UPDATE)) {
            log.warn("Пользователь {} попытался изменить флаг bulkButton без доступа", userId);
            return ResponseBuilder.error(HttpStatus.FORBIDDEN, "Опция недоступна на текущем тарифе");
        }

        userService.updateShowBulkUpdateButton(userId, show);
        return ResponseBuilder.ok("Настройки успешно обновлены.");
    }

    /**
     * Обновляет глобальный флаг Telegram-уведомлений.
     *
     * @param enabled новое значение флага
     * @param user    текущий пользователь
     * @return результат операции
     */
    @PostMapping("/settings/telegram-notifications")
    public ResponseEntity<?> updateTelegramNotifications(
            @RequestParam(value = "enabled", required = false) Boolean enabled,
            @AuthenticationPrincipal User user) {
        Long userId = user.getId();

        if (enabled == null) {
            return ResponseBuilder.error(HttpStatus.BAD_REQUEST, "Не указан параметр enabled");
        }

        if (!subscriptionService.isFeatureEnabled(userId, FeatureKey.TELEGRAM_NOTIFICATIONS)) {
            log.warn("Пользователь {} попытался изменить флаг Telegram без доступа", userId);
            return ResponseBuilder.error(HttpStatus.FORBIDDEN, "Опция недоступна на текущем тарифе");
        }

        userService.updateTelegramNotificationsEnabled(userId, enabled);
        return ResponseBuilder.ok("Настройки успешно обновлены.");
    }

    /**
     * Обрабатывает запросы на изменение настроек пользователя, включая смену пароля.
     * <p>
     * Этот метод выполняет валидацию нового пароля, проверку совпадения паролей и изменение пароля через сервис.
     * В случае ошибки или неверно введённого пароля, возвращается форма с сообщением об ошибке.
     * </p>
     *
     * @param model модель для добавления данных в представление
     * @param passwordChangeDTO DTO для ввода нового пароля
     * @param result результат валидации формы
     * @param user текущий пользователь
     * @return имя представления для части страницы с настройками
     */
    @PostMapping("/settings/password")
    public String updatePassword(Model model,
                                 @Valid @ModelAttribute("passwordChangeDTO") PasswordChangeDTO passwordChangeDTO,
                                 BindingResult result,
                                 @AuthenticationPrincipal User user) {
        Long userId = user.getId();

        if (result.hasErrors()) {
            return "app/profile :: passwordFragment";
        }
        if (!passwordChangeDTO.getNewPassword().equals(passwordChangeDTO.getConfirmPassword())) {
            result.rejectValue("confirmPassword", "password.mismatch", "Пароли не совпадают");
            return "app/profile :: passwordFragment";
        }
        try {
            userService.changePassword(userId, passwordChangeDTO);
            model.addAttribute("notification", "Пароль успешно изменен");
        } catch (IllegalArgumentException e) {
            result.rejectValue("currentPassword", "password.incorrect", e.getMessage());
        }

        return "app/profile :: passwordFragment";
    }

    /**
     * Обрабатывает запрос на удаление учетной записи пользователя.
     * <p>
     * Этот метод удаляет учетную запись пользователя, а затем выполняет выход из системы,
     * перенаправляя пользователя на главную страницу.
     * </p>
     *
     * @param request запрос для получения информации о текущей сессии
     * @param response ответ для выполнения выхода из системы
     * @param user текущий пользователь
     * @return редирект на главную страницу
     */
    @PostMapping("/settings/delete")
    public String delete(HttpServletRequest request, HttpServletResponse response, @AuthenticationPrincipal User user) {
        Long userId = user.getId();
        log.info("Запрос на удаление учетной записи пользователя с ID: {}", userId);

        userService.deleteUser(userId);

        new SecurityContextLogoutHandler().logout(request, response, null);
        log.info("Учетная запись пользователя с ID {} успешно удалена.", userId);

        return "redirect:/";
    }

    /**
     * Возвращает список магазинов пользователя.
     * <p>
     * Перед отправкой данных вызывается сервис {@link StoreService#getDefaultStoreId(Long)},
     * чтобы установить магазин по умолчанию, если у пользователя только один магазин.
     * </p>
     *
     * @param user текущий пользователь
     * @return список магазинов пользователя в виде DTO
     */
    @GetMapping("/stores")
    @ResponseBody
    public List<StoreDTO> getUserStores(@AuthenticationPrincipal User user) {
        storeService.getDefaultStoreId(user.getId());
        return storeService.getUserStoresDto(user.getId());
    }

    /**
     * Создаёт новый магазин пользователя.
     *
     * @param user    текущий пользователь
     * @param request параметры запроса, содержащие название магазина
     * @return созданный магазин либо сообщение об ошибке
     */
    @PostMapping("/stores")
    @ResponseBody
    public ResponseEntity<?> createStore(@AuthenticationPrincipal User user,
                                         @RequestBody Map<String, String> request) {
        try {
            Store store = storeService.createStore(user.getId(), request.get("name"));
            return ResponseBuilder.ok(store);
        } catch (IllegalStateException e) {
            webSocketController.sendUpdateStatus(user.getId(), "❌ Ошибка: " + e.getMessage(), false);
            return ResponseBuilder.error(HttpStatus.FORBIDDEN, e.getMessage());
        }
    }

    /**
     * Обновляет название указанного магазина.
     *
     * @param user    текущий пользователь
     * @param storeId идентификатор магазина
     * @param request параметры запроса с новым именем
     * @return обновленный магазин либо сообщение об ошибке
     */
    @PutMapping("/stores/{storeId}")
    @ResponseBody
    public ResponseEntity<?> updateStore(@AuthenticationPrincipal User user,
                                         @PathVariable Long storeId,
                                         @RequestBody Map<String, String> request) {
        try {
            Store updatedStore = storeService.updateStore(storeId, user.getId(), request.get("name"));
            return ResponseBuilder.ok(updatedStore);
        } catch (SecurityException e) {
            webSocketController.sendUpdateStatus(user.getId(), "❌ Ошибка: " + e.getMessage(), false);
            return ResponseBuilder.error(HttpStatus.FORBIDDEN, e.getMessage());
        }
    }

    /**
     * Удаляет магазин пользователя.
     *
     * @param user    текущий пользователь
     * @param storeId идентификатор магазина
     * @return результат удаления
     */
    @DeleteMapping("/stores/{storeId}")
    @ResponseBody
    public ResponseEntity<?> deleteStore(@AuthenticationPrincipal User user,
                                         @PathVariable Long storeId) {
        try {
            storeService.deleteStore(storeId, user.getId());
            return ResponseBuilder.ok(null);
        } catch (SecurityException e) {
            webSocketController.sendUpdateStatus(user.getId(), "❌ Ошибка: " + e.getMessage(), false);
            return ResponseBuilder.error(HttpStatus.FORBIDDEN, e.getMessage());
        }
    }

    /**
     * Получает текущий лимит магазинов пользователя.
     *
     * @param user Данные текущего пользователя.
     * @return Текущий лимит магазинов (использовано/доступно).
     */
    @GetMapping("/stores/limit")
    @ResponseBody
    public String getStoreLimit(@AuthenticationPrincipal User user) {
        return userService.getUserStoreLimit(user.getId());
    }

    /**
     * Устанавливает магазин по умолчанию для пользователя.
     *
     * @param user    текущий пользователь
     * @param storeId идентификатор магазина
     * @return результат операции
     */
    @PostMapping("/stores/default/{storeId}")
    @ResponseBody
    public ResponseEntity<?> setDefaultStore(@AuthenticationPrincipal User user,
                                             @PathVariable Long storeId) {
        try {
            storeService.setDefaultStore(user.getId(), storeId);
            return ResponseBuilder.ok("Магазин по умолчанию установлен.");
        } catch (Exception e) {
            log.error("Ошибка установки магазина по умолчанию: {}", e.getMessage());
            return ResponseBuilder.error(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    /**
     * Возвращает HTML-фрагмент блока настроек Telegram для магазина.
     *
     * @param storeId        идентификатор магазина
     * @param user  текущий пользователь
     * @param model модель для передачи данных во фрагмент
     * @return HTML-фрагмент блока магазина
     */
    @GetMapping("/stores/{storeId}/telegram-block")
    public String getTelegramBlock(@PathVariable Long storeId,
                                   @AuthenticationPrincipal User user,
                                   Model model) {
        Long userId = user.getId();
        Store store = storeService.getStore(storeId, userId);
        model.addAttribute("store", store);
        return "app/profile :: telegramStoreBlock";
    }

}