package com.project.tracking_system.controller;

import com.project.tracking_system.dto.UserRegistrationDTO;
import com.project.tracking_system.exception.UserAlreadyExistsException;
import com.project.tracking_system.service.user.LoginAttemptService;
import com.project.tracking_system.service.user.RegistrationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Контроллер для регистрации и авторизации пользователей.
 * <p>
 * Обрабатывает показ страницы регистрации, сам процесс регистрации
 * и отображение формы входа с учётом блокировок по email и IP.
 * </p>
 */
@Slf4j
@RequiredArgsConstructor
@Controller
@RequestMapping("/auth")
public class AuthController {

    private final LoginAttemptService loginAttemptService;
    private final RegistrationService registrationService;

    /**
     * Отображает страницу регистрации нового пользователя.
     *
     * @param userRegistrationDTO DTO для регистрации пользователя
     * @param model модель для добавления данных в представление
     * @return имя представления страницы регистрации
     */
    @GetMapping("/registration")
    public String registration(@ModelAttribute("userDTO") UserRegistrationDTO userRegistrationDTO, Model model) {
        model.addAttribute("userDTO", new UserRegistrationDTO());
        return "auth/registration";
    }

    /**
     * Обрабатывает POST-запросы на регистрацию пользователя.
     * Выполняет отправку кода подтверждения и финальное подтверждение.
     *
     * @param userDTO DTO для регистрации пользователя
     * @param result результат валидации формы
     * @param model модель для добавления данных в представление
     * @return имя представления для регистрации
     */
    @PostMapping("/registration")
    public String registration(@Valid @ModelAttribute("userDTO") UserRegistrationDTO userDTO,
                               BindingResult result, Model model) {
        if (registrationService.isInitialStep(userDTO)) {
            try {
                if (registrationService.handleInitialStep(userDTO, result)) {
                    model.addAttribute("confirmCodRegistration", true);
                    model.addAttribute("message", "Код подтверждения отправлен на вашу почту.");
                }
            } catch (UserAlreadyExistsException e) {
                model.addAttribute("errorMessage", "Данная почта уже используется, авторизуйтесь или используйте другую почту");
            } catch (Exception e) {
                model.addAttribute("errorMessage", "Ошибка регистрации пользователя: " + e.getMessage());
            }
            return "auth/registration";
        }

        if (result.hasFieldErrors("confirmCodRegistration")) {
            model.addAttribute("confirmCodRegistration", true);
            return "auth/registration";
        }

        try {
            registrationService.confirm(userDTO);
            return "redirect:/auth/login";
        } catch (IllegalArgumentException e) {
            model.addAttribute("confirmCodRegistration", true);
            model.addAttribute("errorMessage", e.getMessage());
        }
            return "auth/registration";
    }

    /**
     * Отображает страницу входа в систему.
     *
     * @param error сообщение об ошибке входа
     * @param blocked сообщение о блокировке аккаунта
     * @param blockedIP сообщение о блокировке IP
     * @param session сессия для проверки состояния пользователя
     * @param model модель для добавления данных в представление
     * @param principal информация о текущем пользователе
     * @param request HTTP-запрос для получения IP
     * @return имя представления страницы входа
     */
    @GetMapping("/login")
    public String loginPage(@RequestParam(value = "error", required = false) String error,
                            @RequestParam(value = "blocked", required = false) String blocked,
                            @RequestParam(value = "blockedIP", required = false) String blockedIP,
                            HttpSession session, Model model, Principal principal, HttpServletRequest request) {
        if (principal != null) {
            return "redirect:/";
        }

        String email = (String) session.getAttribute("email");

        if (blocked != null && email != null) {
            ZonedDateTime unlockTime = loginAttemptService.getUnlockTime(email);
            model.addAttribute("blockedMessage", "Ваш аккаунт заблокирован из-за превышения количества попыток входа. Попробуйте снова после: "
                    + unlockTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        }

        if (blockedIP != null) {
            String ip = request.getRemoteAddr();
            model.addAttribute("blockedIPMessage", "Ваш IP (" + ip + ") временно заблокирован из-за множественных неудачных попыток входа. Попробуйте снова позже.");
        }

        if (error != null && email != null) {
            int remainingAttempts = loginAttemptService.getRemainingAttempts(email);
            model.addAttribute("remainingAttempts", remainingAttempts);
        }

        return "auth/login";
    }

}