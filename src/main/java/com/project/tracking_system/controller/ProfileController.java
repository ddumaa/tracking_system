package com.project.tracking_system.controller;

import com.project.tracking_system.dto.EvropostCredentialsDTO;
import com.project.tracking_system.dto.UserSettingsDTO;
import com.project.tracking_system.entity.Store;
import com.project.tracking_system.entity.User;
import com.project.tracking_system.service.store.StoreService;
import com.project.tracking_system.service.user.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import com.project.tracking_system.utils.ResponseBuilder;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.Authentication;
import com.project.tracking_system.utils.AuthUtils;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.security.Principal;


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

    /**
     * Отображает страницу профиля пользователя.
     * <p>
     * Этот метод извлекает информацию о текущем пользователе через объект {@link Principal}
     * и передает её в модель для отображения на странице.
     * </p>
     *
     * @param model модель для добавления данных в представление
     * @return имя представления страницы профиля
     */
    @GetMapping
    public String profile(Model model, Principal principal) {
        // Получаем пользователя по текущему Principal
        User user = userService.findByUserEmail(principal.getName())
                .orElseThrow(() -> new IllegalArgumentException("Пользователь не найден"));
        Long userId = user.getId();
        log.info("Получен запрос на отображение профиля для пользователя с ID: {}", userId);

        String storeLimit = userService.getUserStoreLimit(userId);

        // Загружаем магазины с настройками Telegram
        List<Store> stores = storeService.findAllOwnedByUser(principal);

        // Добавляем данные профиля в модель
        model.addAttribute("username", user.getEmail());
        model.addAttribute("storeLimit", storeLimit);
        model.addAttribute("stores", stores);
        log.debug("Данные профиля добавлены в модель для пользователя с ID: {}", userId);

        // Добавляем настройки и другие данные пользователя в модель
        model.addAttribute("userSettingsDTO", new UserSettingsDTO());
        model.addAttribute("evropostCredentialsDTO", userService.getEvropostCredentials(userId));

        return "profile";
    }

    /**
     * Отображает форму настроек пользователя.
     * <p>
     * Этот метод подготавливает форму для изменения настроек пользователя (например, смены пароля).
     * </p>
     *
     * @param model модель для добавления данных в представление
     * @return имя представления для части страницы с настройками
     */
    @GetMapping("/settings")
    public String settings(
            @RequestParam(value = "tab", required = false, defaultValue = "password") String tab,
            Model model,
            Authentication authentication) {

        User user = AuthUtils.getCurrentUser(authentication);
        Long userId = user.getId();
        model.addAttribute("userSettingsDTO", new UserSettingsDTO());

        if ("evropost".equals(tab)) {
            model.addAttribute("evropostCredentialsDTO", userService.getEvropostCredentials(userId));
            log.debug("Форма Европочты подготовлена для пользователя с ID: {}", userId);
        } else {
            log.debug("Открыта вкладка по умолчанию (пароль) для пользователя с ID: {}", userId);
        }

        return "profile";
    }

    @PostMapping("/settings/evropost")
    public String updateEvropostCredentials(
            @Valid @ModelAttribute("evropostCredentialsDTO") EvropostCredentialsDTO evropostCredentialsDTO,
            BindingResult bindingResult, Model model, Authentication authentication) {

        Long userId = AuthUtils.getCurrentUser(authentication).getId();

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

        return "profile :: evropostFragment";
    }

    @PostMapping("/settings/use-custom-credentials")
    public ResponseEntity<?> updateUseCustomCredentials(
            @RequestParam(value = "useCustomCredentials", required = false) Boolean useCustomCredentials,
            Authentication authentication) {

        Long userId = AuthUtils.getCurrentUser(authentication).getId();

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
     * Обрабатывает запросы на изменение настроек пользователя, включая смену пароля.
     * <p>
     * Этот метод выполняет валидацию нового пароля, проверку совпадения паролей и изменение пароля через сервис.
     * В случае ошибки или неверно введённого пароля, возвращается форма с сообщением об ошибке.
     * </p>
     *
     * @param model модель для добавления данных в представление
     * @param userSettingsDTO DTO для ввода настроек пользователя (включая новый пароль)
     * @param result результат валидации формы
     * @return имя представления для части страницы с настройками
     */
    @PostMapping("/settings/password")
    public String updatePassword(Model model,
                                 @Valid @ModelAttribute("userSettingsDTO") UserSettingsDTO userSettingsDTO,
                                 BindingResult result,
                                 Authentication authentication) {
        Long userId = AuthUtils.getCurrentUser(authentication).getId();

        if (result.hasErrors()) {
            return "profile :: passwordFragment";
        }
        if (!userSettingsDTO.getNewPassword().equals(userSettingsDTO.getConfirmPassword())) {
            result.rejectValue("confirmPassword", "password.mismatch", "Пароли не совпадают");
            return "profile :: passwordFragment";
        }
        try {
            userService.changePassword(userId, userSettingsDTO);
            model.addAttribute("notification", "Пароль успешно изменен");
        } catch (IllegalArgumentException e) {
            result.rejectValue("currentPassword", "password.incorrect", e.getMessage());
        }

        return "profile :: passwordFragment";
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
     * @return редирект на главную страницу
     */
    @PostMapping("/settings/delete")
    public String delete(HttpServletRequest request, HttpServletResponse response, Authentication authentication) {
        User user = AuthUtils.getCurrentUser(authentication);
        Long userId = user.getId();
        log.info("Запрос на удаление учетной записи пользователя с ID: {}", userId);

        userService.deleteUser(userId);

        new SecurityContextLogoutHandler().logout(request, response, authentication);
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
     * @return список магазинов пользователя
     */
    @GetMapping("/stores")
    @ResponseBody
    public List<Store> getUserStores(@AuthenticationPrincipal User user) {
        storeService.getDefaultStoreId(user.getId());
        return storeService.getUserStores(user.getId());
    }

    /**
     * Создаёт новый магазин.
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
     * Обновляет название магазина.
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
     * Удаляет магазин.
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
     * @param authentication Данные текущего пользователя.
     * @return Текущий лимит магазинов (использовано/доступно).
     */
    @GetMapping("/stores/limit")
    @ResponseBody
    public String getStoreLimit(Authentication authentication) {
        User user = AuthUtils.getCurrentUser(authentication);
        return userService.getUserStoreLimit(user.getId());
    }

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
     * Возвращает HTML-фрагмент блока магазина с Telegram-настройками.
     *
     * @param id        идентификатор магазина
     * @param model     модель представления
     * @param principal текущий пользователь
     * @return фрагмент HTML магазина
     */
    @GetMapping("/stores/partials/{id}")
    public String getStoreFragment(@PathVariable Long id, Model model, Principal principal) {
        Store store = storeService.findOwnedByUser(id, principal);
        model.addAttribute("store", store);
        return "partials/tg_bot_store :: storeBlock";
    }


}