package com.project.tracking_system.controller;

import com.project.tracking_system.dto.EvropostCredentialsDTO;
import com.project.tracking_system.dto.UserSettingsDTO;
import com.project.tracking_system.entity.User;
import com.project.tracking_system.service.user.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.Optional;

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
@RequestMapping("/profile")
public class ProfileController {

    private static final Logger logger = LoggerFactory.getLogger(ProfileController.class);

    private final UserService userService;

    @Autowired
    public ProfileController(UserService userService) {
        this.userService = userService;
    }

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
    public String profile(Model model) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String username = authentication.getName();
        logger.info("Получен запрос на отображение профиля для пользователя: {}", username);

        Optional<User> user = userService.findByUser(username);
        user.ifPresentOrElse(
                u -> {
                    model.addAttribute("username", u.getEmail());
                    logger.debug("Данные профиля добавлены в модель для пользователя: {}", u.getEmail());
                },
                () -> logger.warn("Пользователь с именем {} не найден в базе данных.", username)
        );

        model.addAttribute("userSettingsDTO", new UserSettingsDTO());
        model.addAttribute("evropostCredentialsDTO", userService.getEvropostCredentials(username));


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
            Model model) {

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String username = authentication.getName();

        model.addAttribute("userSettingsDTO", new UserSettingsDTO());

        if ("evropost".equals(tab)) {
            model.addAttribute("evropostCredentialsDTO", userService.getEvropostCredentials(username));
            logger.debug("Форма Европочты подготовлена для пользователя: {}", username);
        } else {
            logger.debug("Открыта вкладка по умолчанию (пароль) для пользователя: {}", username);
        }

        return "profile";
    }


    @PostMapping("/settings/evropost")
    public String updateEvropostCredentials(
            @Valid @ModelAttribute("evropostCredentialsDTO") EvropostCredentialsDTO evropostCredentialsDTO,
            BindingResult bindingResult, Model model) {

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String email = authentication.getName();
        logger.info("Запрос на обновление данных Европочты для пользователя: {}", email);

        // Проверяем ошибки валидации
        if (bindingResult.hasErrors()) {
            logger.warn("Обнаружены ошибки валидации для данных Европочты пользователя: {}", email);
            model.addAttribute("evropostCredentialsDTO", evropostCredentialsDTO);
            return "profile :: #v-pills-evropost";
        }

        try {
            userService.updateEvropostCredentialsAndSettings(email, evropostCredentialsDTO);
            logger.info("Данные Европочты успешно обновлены для пользователя: {}", email);

            EvropostCredentialsDTO updatedDto = userService.getEvropostCredentials(email);
            model.addAttribute("evropostCredentialsDTO", updatedDto);
            model.addAttribute("notification", "Данные Европочты успешно обновлены");

            return "profile :: #v-pills-evropost";
        } catch (Exception e) {
            logger.error("Ошибка при обновлении данных Европочты для пользователя {}: {}", email, e.getMessage(), e);
        }

        return "profile :: #v-pills-evropost";
    }

    @PostMapping("/settings/use-custom-credentials")
    public ResponseEntity<String> updateUseCustomCredentials(
            @RequestParam(value = "useCustomCredentials", required = false) Boolean useCustomCredentials,
            Principal principal) {

        String email = principal.getName();
        logger.info("Запрос на обновление флага 'useCustomCredentials' для пользователя: {}", email);

        // Проверяем наличие параметра useCustomCredentials
        if (useCustomCredentials == null) {
            logger.warn("Не указан параметр 'useCustomCredentials' для пользователя: {}", email);
            return ResponseEntity.badRequest().body("Не указан параметр useCustomCredentials");
        }

        try {
            // Обновляем данные в БД
            userService.updateUseCustomCredentials(email, useCustomCredentials);
            logger.info("Флаг 'useCustomCredentials' успешно обновлён для пользователя: {}", email);
            return ResponseEntity.ok("Настройки успешно обновлены.");
        } catch (Exception e) {
            logger.error("Ошибка при обновлении настройки для пользователя {}: {}", email, e.getMessage(), e);
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
    public String updatePassword(Model model, @Valid @ModelAttribute("userSettingsDTO") UserSettingsDTO userSettingsDTO,
                                 BindingResult result) {

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String email = authentication.getName();
        logger.info("Запрос на смену пароля для пользователя: {}", email);

        if (result.hasErrors()) {
            logger.warn("Обнаружены ошибки валидации при смене пароля для пользователя: {}", email);
            return "profile :: #v-pills-profile";
        }
        if (!userSettingsDTO.getNewPassword().equals(userSettingsDTO.getConfirmPassword())) {
            logger.warn("Пароли не совпадают для пользователя: {}", email);
            result.rejectValue("confirmPassword", "password.mismatch", "Пароли не совпадают");
            return "profile :: #v-pills-profile";
        }

        try {
            userService.changePassword(email, userSettingsDTO);
            logger.info("Пароль успешно изменен для пользователя: {}", email);
            model.addAttribute("notification", "Пароль успешно изменен");
            return "profile :: #v-pills-profile";
        } catch (IllegalArgumentException e) {
            logger.error("Ошибка при смене пароля для пользователя {}: {}", email, e.getMessage());
            result.rejectValue("currentPassword", "password.incorrect", e.getMessage());
        }
        return "profile :: #v-pills-profile";
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
    public String delete(HttpServletRequest request, HttpServletResponse response) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String email = authentication != null ? authentication.getName() : "неизвестный пользователь";
        logger.info("Запрос на удаление учетной записи пользователя: {}", email);

        userService.deleteUser();
        if (authentication != null) {
            new SecurityContextLogoutHandler().logout(request, response, authentication);
        }

        logger.info("Учетная запись пользователя {} успешно удалена.", email);
        return "redirect:/";
    }
}