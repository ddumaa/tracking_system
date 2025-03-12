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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
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
    public String profile(Model model, Authentication authentication) {
        if (!(authentication instanceof UsernamePasswordAuthenticationToken auth) || !(auth.getPrincipal() instanceof User user)) {
            log.warn("Попытка доступа к профилю неаутентифицированного пользователя.");
            return "redirect:/login"; // Перенаправляем на страницу входа
        }

        Long userId = user.getId();
        log.info("Получен запрос на отображение профиля для пользователя с ID: {}", userId);

        String storeLimit = userService.getUserStoreLimit(userId);

        // Добавляем данные профиля в модель
        model.addAttribute("username", user.getEmail());
        model.addAttribute("storeLimit", storeLimit);
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

        if (!(authentication instanceof UsernamePasswordAuthenticationToken auth) || !(auth.getPrincipal() instanceof User user)) {
            log.warn("Попытка доступа к настройкам без аутентификации.");
            return "redirect:/login"; // Перенаправление, если пользователь не аутентифицирован
        }

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

        if (!(authentication instanceof UsernamePasswordAuthenticationToken auth) || !(auth.getPrincipal() instanceof User user)) {
            log.warn("Попытка обновления данных Европочты без аутентификации.");
            return "redirect:/login"; // Защита от неаутентифицированных пользователей
        }

        Long userId = user.getId();
        log.info("Запрос на обновление данных Европочты для пользователя с ID: {}", userId);

        // Проверяем ошибки валидации
        if (bindingResult.hasErrors()) {
            log.warn("Обнаружены ошибки валидации для данных Европочты пользователя с ID: {}", userId);
            model.addAttribute("evropostCredentialsDTO", evropostCredentialsDTO);
        } else {
            try {
                userService.updateEvropostCredentialsAndSettings(userId, evropostCredentialsDTO);
                log.info("Данные Европочты успешно обновлены для пользователя с ID: {}", userId);

                EvropostCredentialsDTO updatedDto = userService.getEvropostCredentials(userId);
                model.addAttribute("evropostCredentialsDTO", updatedDto);

                // ✅ Отправляем WebSocket-уведомление
                String successMessage = "Данные API Европочты успешно обновлены!";
                webSocketController.sendUpdateStatus(userId, successMessage, true);

            } catch (Exception e) {
                log.error("Ошибка при обновлении данных Европочты для пользователя с ID {}: {}", userId, e.getMessage(), e);
                model.addAttribute("error", "Ошибка при обновлении данных: " + e.getMessage());

                // ❌ Отправляем WebSocket-уведомление об ошибке
                webSocketController.sendUpdateStatus(userId, "Ошибка обновления Европочты!", false);
            }
        }

        return "profile :: evropostFragment";
    }

    @PostMapping("/settings/use-custom-credentials")
    public ResponseEntity<String> updateUseCustomCredentials(
            @RequestParam(value = "useCustomCredentials", required = false) Boolean useCustomCredentials,
            Authentication authentication) {

        if (!(authentication instanceof UsernamePasswordAuthenticationToken auth) || !(auth.getPrincipal() instanceof User user)) {
            log.warn("Попытка обновления настроек без аутентификации.");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Необходима аутентификация");
        }

        Long userId = user.getId();
        log.info("Запрос на обновление флага 'useCustomCredentials' для пользователя с ID: {}", userId);

        if (useCustomCredentials == null) {
            log.warn("Не указан параметр 'useCustomCredentials' для пользователя с ID: {}", userId);
            return ResponseEntity.badRequest().body("Не указан параметр useCustomCredentials");
        }

        try {
            userService.updateUseCustomCredentials(userId, useCustomCredentials);
            log.info("Флаг 'useCustomCredentials' успешно обновлён для пользователя с ID: {}", userId);
            return ResponseEntity.ok("Настройки успешно обновлены.");
        } catch (Exception e) {
            log.error("Ошибка при обновлении настройки для пользователя с ID {}: {}", userId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Ошибка при обновлении настроек.");
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
        if (!(authentication instanceof UsernamePasswordAuthenticationToken auth) || !(auth.getPrincipal() instanceof User user)) {
            log.warn("Попытка смены пароля без аутентификации.");
            return "redirect:/login"; // Защита от неаутентифицированных пользователей
        }

        Long userId = user.getId();
        log.info("Запрос на смену пароля для пользователя с ID: {}", userId);

        if (result.hasErrors()) {
            log.warn("Обнаружены ошибки валидации при смене пароля для пользователя с ID: {}", userId);
        } else if (!userSettingsDTO.getNewPassword().equals(userSettingsDTO.getConfirmPassword())) {
            log.warn("Пароли не совпадают для пользователя с ID: {}", userId);
            result.rejectValue("confirmPassword", "password.mismatch", "Пароли не совпадают");
        } else {
            try {
                userService.changePassword(userId, userSettingsDTO);
                log.info("Пароль успешно изменен для пользователя с ID: {}", userId);
                model.addAttribute("notification", "Пароль успешно изменен");
            } catch (IllegalArgumentException e) {
                log.error("Ошибка при смене пароля для пользователя с ID {}: {}", userId, e.getMessage());
                result.rejectValue("currentPassword", "password.incorrect", e.getMessage());
            }
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
        if (authentication == null || !(authentication.getPrincipal() instanceof User user)) {
            log.warn("Попытка удаления учетной записи без аутентификации.");
            return "redirect:/login"; // Отправляем на логин, если пользователь не аутентифицирован
        }

        Long userId = user.getId();
        log.info("Запрос на удаление учетной записи пользователя с ID: {}", userId);

        userService.deleteUser(userId);

        new SecurityContextLogoutHandler().logout(request, response, authentication);
        log.info("Учетная запись пользователя с ID {} успешно удалена.", userId);

        return "redirect:/";
    }

    /**
     * Получает список магазинов пользователя.
     */
    @GetMapping("/stores")
    @ResponseBody
    public List<Store> getUserStores(@AuthenticationPrincipal User user) {
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
            return ResponseEntity.ok(store);
        } catch (IllegalStateException e) {
            webSocketController.sendUpdateStatus(user.getId(), "❌ Ошибка: " + e.getMessage(), false);
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage());
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
            return ResponseEntity.ok(updatedStore);
        } catch (SecurityException e) {
            webSocketController.sendUpdateStatus(user.getId(), "❌ Ошибка: " + e.getMessage(), false);
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage());
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
            return ResponseEntity.ok().build();
        } catch (SecurityException e) {
            webSocketController.sendUpdateStatus(user.getId(), "❌ Ошибка: " + e.getMessage(), false);
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage());
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
        if (!(authentication instanceof UsernamePasswordAuthenticationToken auth) || !(auth.getPrincipal() instanceof User user)) {
            throw new SecurityException("Необходима аутентификация");
        }

        return userService.getUserStoreLimit(user.getId());
    }

    @PostMapping("/stores/default/{storeId}")
    @ResponseBody
    public ResponseEntity<String> setDefaultStore(@AuthenticationPrincipal User user,
                                                  @PathVariable Long storeId) {
        try {
            storeService.setDefaultStore(user.getId(), storeId);
            return ResponseEntity.ok("Магазин по умолчанию установлен.");
        } catch (Exception e) {
            log.error("Ошибка установки магазина по умолчанию: {}", e.getMessage());
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }


}