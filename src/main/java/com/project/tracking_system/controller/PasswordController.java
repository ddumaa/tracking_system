package com.project.tracking_system.controller;

import com.project.tracking_system.dto.PasswordResetDTO;
import com.project.tracking_system.entity.User;
import com.project.tracking_system.service.user.PasswordResetService;
import com.project.tracking_system.service.user.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

/**
 * Контроллер для восстановления и сброса пароля пользователя.
 */
@Slf4j
@RequiredArgsConstructor
@Controller
@RequestMapping("/")
public class PasswordController {

    private final PasswordResetService passwordResetService;
    private final UserService userService;

    /**
     * Отображает страницу для восстановления пароля.
     *
     * @param model модель для добавления данных в представление
     * @return имя представления страницы восстановления пароля
     */
    @GetMapping("/forgot-password")
    public String showForgotPasswordForm(Model model) {
        model.addAttribute("email", "");
        return "forgot-password";
    }

    /**
     * Обрабатывает запросы на восстановление пароля.
     *
     * @param email адрес электронной почты для восстановления пароля
     * @param model модель для добавления данных в представление
     * @return имя представления страницы восстановления пароля
     */
    @PostMapping("/forgot-password")
    public String handleForgotPassword(@RequestParam String email, Model model) {
        Optional<User> byUser = userService.findByUserEmail(email);
        if (byUser.isEmpty()) {
            model.addAttribute("error", "Пользователь с таким адресом электронной почты не найден.");
            return "forgot-password";
        }
        try {
            passwordResetService.createPasswordResetToken(email);
            model.addAttribute("message", "Ссылка для сброса пароля была отправлена на ваш адрес электронной почты.");
        } catch (UsernameNotFoundException e) {
            model.addAttribute("error", "Пользователь с таким адресом электронной почты не найден.");
        } catch (Exception e) {
            model.addAttribute("error", "Не удалось выполнить сброс пароля. Попробуйте снова.");
        }
        return "forgot-password";
    }

    /**
     * Отображает страницу для сброса пароля по токену.
     *
     * @param token токен для сброса пароля
     * @param passwordResetDTO DTO для сброса пароля
     * @param model модель для добавления данных в представление
     * @return имя представления страницы сброса пароля
     */
    @GetMapping("/reset-password")
    public String showResetPasswordForm(@RequestParam String token,
                                         @ModelAttribute("passwordResetDTO") PasswordResetDTO passwordResetDTO,
                                         Model model) {
        if (!passwordResetService.isTokenValid(token)) {
            model.addAttribute("error", "Неверный или просроченный токен.");
            return "forgot-password";
        }
        model.addAttribute("token", token);
        return "reset-password";
    }

    /**
     * Обрабатывает запросы на сброс пароля по токену.
     *
     * @param token токен для сброса пароля
     * @param passwordResetDTO DTO для сброса пароля
     * @param bindingResult результат валидации формы
     * @param model модель для добавления данных в представление
     * @return имя представления страницы сброса пароля
     */
    @PostMapping("/reset-password")
    public String handleResetPassword(@RequestParam String token,
                                      @Valid @ModelAttribute("passwordResetDTO") PasswordResetDTO passwordResetDTO,
                                      BindingResult bindingResult,
                                      Model model) {

        if (bindingResult.hasErrors()) {
            model.addAttribute("token", token);
            return "reset-password";
        }

        if (!passwordResetDTO.passwordsMatch()) {
            bindingResult.rejectValue("confirmPassword", "error.confirmPassword", "Пароли не совпадают");
            model.addAttribute("token", token);
            return "reset-password";
        }

        try {
            passwordResetService.resetPassword(token, passwordResetDTO.getNewPassword());
            model.addAttribute("message", "Ваш пароль успешно сброшен. Пожалуйста, войдите с новым паролем.");
            return "login";
        } catch (IllegalArgumentException e) {
            model.addAttribute("error", "Токен сброса пароля недействителен или срок его действия истек.");
            return "reset-password";
        } catch (Exception e) {
            model.addAttribute("error", "Не удалось сбросить пароль. Попробуйте снова.");
            return "reset-password";
        }
    }
}
