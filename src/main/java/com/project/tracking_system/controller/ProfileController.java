package com.project.tracking_system.controller;

import com.project.tracking_system.dto.UserSettingsDTO;
import com.project.tracking_system.entity.User;
import com.project.tracking_system.service.user.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

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
        Optional<User> user = userService.findByUser(username);
        user.ifPresent(u -> model.addAttribute("username", u.getEmail()));
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
    public String settings(Model model) {
        model.addAttribute("userSettingsDTO", new UserSettingsDTO());
        return "settings :: form";
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
    @PostMapping("/settings")
    public String settings(Model model, @Valid @ModelAttribute("userSettingsDTO") UserSettingsDTO userSettingsDTO, BindingResult result) {
        if (result.hasErrors()) {
            return "settings :: form";
        }
        if (!userSettingsDTO.getNewPassword().equals(userSettingsDTO.getConfirmPassword())) {
            result.rejectValue("confirmPassword", "password.mismatch", "Пароли не совпадают");
            return "settings :: form";
        }
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String email = authentication.getName();
        try {
            userService.changePassword(email, userSettingsDTO);
            model.addAttribute("notification", "Пароль успешно изменен");
            return "settings :: form";
        } catch (IllegalArgumentException e) {
            result.rejectValue("currentPassword", "password.incorrect", e.getMessage());
        }
        return "settings :: form";
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
        userService.deleteUser();
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null) {
            new SecurityContextLogoutHandler().logout(request, response, authentication);
        }
        return "redirect:/";
    }
}