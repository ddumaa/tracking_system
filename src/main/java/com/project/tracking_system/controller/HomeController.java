package com.project.tracking_system.controller;

import com.project.tracking_system.dto.PasswordResetDTO;
import com.project.tracking_system.dto.TrackingResultAdd;
import com.project.tracking_system.dto.UserRegistrationDTO;
import com.project.tracking_system.dto.TrackInfoListDTO;
import com.project.tracking_system.entity.User;
import com.project.tracking_system.exception.UserAlreadyExistsException;
import com.project.tracking_system.service.TrackNumberOcrService;
import com.project.tracking_system.service.TrackingNumberServiceXLS;
import com.project.tracking_system.service.user.LoginAttemptService;
import com.project.tracking_system.service.TypeDefinitionTrackPostService;
import com.project.tracking_system.service.TrackParcelService;
import com.project.tracking_system.service.user.PasswordResetService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import com.project.tracking_system.service.user.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.security.Principal;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

/**
 * Контроллер для обработки запросов на главной странице, регистрации, входа, сброса пароля и загрузки файлов.
 * <p>
 * Этот контроллер предоставляет методы для отображения домашней страницы, обработки регистрации пользователя,
 * входа в систему, сброса пароля и загрузки файлов (в том числе изображений и XLS/XLSX файлов).
 * Также контроллер взаимодействует с сервисами для обработки информации об отслеживаемых посылках.
 * </p>
 *
 * @author Dmitriy Anisimov
 * @date 07.01.2025
 */
@Controller
@RequestMapping("/")
public class HomeController {

    private final UserService userService;
    private final TrackParcelService trackParcelService;
    private final TypeDefinitionTrackPostService typeDefinitionTrackPostService;
    private final LoginAttemptService loginAttemptService;
    private final PasswordResetService passwordResetService;
    private final TrackingNumberServiceXLS trackingNumberServiceXLS;
    //private final TrackNumberOcrService trackNumberOcrService;

    @Autowired
    public HomeController(UserService userService, TrackParcelService trackParcelService,
                          TypeDefinitionTrackPostService typeDefinitionTrackPostService,
                          LoginAttemptService loginAttemptService,
                          PasswordResetService passwordResetService,
                          TrackingNumberServiceXLS trackingNumberServiceXLS) {
        this.userService = userService;
        this.trackParcelService = trackParcelService;
        this.typeDefinitionTrackPostService = typeDefinitionTrackPostService;
        this.loginAttemptService = loginAttemptService;
        this.passwordResetService = passwordResetService;
        this.trackingNumberServiceXLS = trackingNumberServiceXLS;
        //this.trackNumberOcrService = trackNumberOcrService;
    }

    /**
     * Обрабатывает запросы на главной странице. Отображает домашнюю страницу.
     *
     * @return имя представления домашней страницы
     */
    @GetMapping
    public String home() {
        return "home";
    }

    /**
     * Обрабатывает POST-запросы на главной странице. Выполняет отслеживание посылки по номеру.
     * Отображает информацию о посылке и сохраняет данные отслеживания для авторизованного пользователя.
     *
     * @param number номер посылки для отслеживания
     * @param model модель для добавления данных в представление
     * @param request запрос для получения информации о сессии
     * @return имя представления домашней страницы
     */
    @PostMapping
    public String home(@ModelAttribute("number") String number, Model model, HttpServletRequest request) {
        HttpSession session = request.getSession();
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        // Проверка на аутентификацию
        boolean isAuthenticated = auth != null && auth.isAuthenticated() && !(auth instanceof AnonymousAuthenticationToken);
        User user = isAuthenticated ? (User) auth.getPrincipal() : null;

        model.addAttribute("number", number);
        model.addAttribute("authenticatedUser", user != null ? user.getEmail() : null);

        try {
            // Получение данных посылки
            TrackInfoListDTO trackInfo = typeDefinitionTrackPostService.getTypeDefinitionTrackPostService(user, number);
            model.addAttribute("trackInfo", trackInfo);

            // Если данные пустые
            if (trackInfo.getList().isEmpty()) {
                model.addAttribute("customError", "Нет данных для указанного номера посылки.");
            }

            // Сохраняем данные, если пользователь аутентифицирован
            if (isAuthenticated) {
                session.setAttribute("userSession", user.getEmail());
                trackParcelService.save(number, trackInfo, user);
            } else {
                session.removeAttribute("userSession");
            }

        } catch (IllegalArgumentException e) {
            model.addAttribute("customError", e.getMessage());
        } catch (Exception e) {
            model.addAttribute("generalError", "Произошла ошибка при обработке запроса.");
        }

        return "home";
    }

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
        return "registration";
    }

    /**
     * Обрабатывает POST-запросы на регистрацию пользователя.
     * Выполняет проверку и регистрацию пользователя, отправку кода подтверждения.
     *
     * @param userDTO DTO для регистрации пользователя
     * @param result результат валидации формы
     * @param model модель для добавления данных в представление
     * @return имя представления для регистрации
     */
    @PostMapping("/registration")
    public String registration(@Valid @ModelAttribute("userDTO") UserRegistrationDTO userDTO, BindingResult result, Model model) {
        // Проверка, на каком этапе находится процесс регистрации
        boolean isInitialRegistration = userDTO.getConfirmCodRegistration() == null || userDTO.getConfirmCodRegistration().isEmpty();

        // Первый этап регистрации: проверка email и паролей
        if (isInitialRegistration) {
            if (result.hasFieldErrors("email") || result.hasFieldErrors("password") || result.hasFieldErrors("confirmPassword") || result.hasFieldErrors("agreeToTerms")) {
                return "registration";
            }

            if (!userDTO.getPassword().equals(userDTO.getConfirmPassword())) {
                result.rejectValue("confirmPassword", "password.mismatch", "Пароли не совпадают");
                return "registration";
            }

            try {
                // Отправка кода подтверждения и отображение поля для его ввода
                userService.sendConfirmationCode(userDTO);
                model.addAttribute("confirmCodRegistration", true);
                model.addAttribute("message", "Код подтверждения отправлен на вашу почту.");
                return "registration";
            } catch (UserAlreadyExistsException e) {
                model.addAttribute("errorMessage", "Данная почта уже используется, авторизуйтесь или используйте другую почту");
                return "registration";
            } catch (Exception e) {
                model.addAttribute("errorMessage", "Ошибка регистрации пользователя: " + e.getMessage());
                return "registration";
            }
        } else {
            // Проверка кода подтверждения
            if (result.hasFieldErrors("confirmCodRegistration")) {
                model.addAttribute("confirmCodRegistration", true);
                return "registration";
            }

            try {
                userService.confirmRegistration(userDTO);
                return "redirect:/login";
            } catch (IllegalArgumentException e) {
                model.addAttribute("confirmCodRegistration", true);
                model.addAttribute("errorMessage", e.getMessage());
                return "registration";
            } catch (UserAlreadyExistsException e) {
                model.addAttribute("errorMessage", "Данная почта уже используется, авторизуйтесь или используйте другую почту");
                return "registration";
            } catch (Exception e) {
                model.addAttribute("errorMessage", "Ошибка регистрации пользователя: " + e.getMessage());
                return "registration";
            }
        }
    }

    /**
     * Отображает страницу входа в систему.
     *
     * @param error сообщение об ошибке входа
     * @param blocked сообщение о блокировке аккаунта
     * @param session сессия для проверки состояния пользователя
     * @param model модель для добавления данных в представление
     * @param principal информация о текущем пользователе
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
            model.addAttribute("blockedMessage", "Ваш аккаунт заблокирован из-за превышения количества попыток входа. Попробуйте снова после: " + unlockTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        }

        if (blockedIP != null) {
            String ip = request.getRemoteAddr();
            model.addAttribute("blockedIPMessage", "Ваш IP (" + ip + ") временно заблокирован из-за множественных неудачных попыток входа. Попробуйте снова позже.");
        }

        if (error != null && email != null) {
            int remainingAttempts = loginAttemptService.getRemainingAttempts(email);
            model.addAttribute("remainingAttempts", remainingAttempts);
        }

        return "login";
    }

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
        Optional<User> byUser = userService.findByUser(email);
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
    public String showResetPasswordForm(@RequestParam String token, @ModelAttribute("passwordResetDTO") PasswordResetDTO passwordResetDTO, Model model) {
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

    /**
     * Обрабатывает загрузку файла (XLS, XLSX или изображения).
     * В зависимости от типа файла выполняется обработка данных или распознавание текста.
     *
     * @param file загружаемый файл
     * @param model модель для добавления данных в представление
     * @return имя представления домашней страницы
     */
    /*
    @PostMapping("/upload")
    public String uploadFile(@RequestParam("file") MultipartFile file, Model model) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        User user = null;

        if (auth != null && auth.isAuthenticated() && !(auth instanceof AnonymousAuthenticationToken)) {
        // Если пользователь авторизован, извлекаем объект User
        user = (User) auth.getPrincipal();
        model.addAttribute("authenticatedUser", user.getEmail());
    } else {
        // Если пользователь не авторизован
        model.addAttribute("authenticatedUser", null);
    }

        if (file.isEmpty()) {
            model.addAttribute("customError", "Пожалуйста, выберите XLS, XLSX или изображение для загрузки.");
            return "home";
        }

        // Определяем MIME-тип файла
        String contentType = file.getContentType();
        if (contentType == null) {
            model.addAttribute("customError", "Не удалось определить тип файла.");
            return "home";
        }

        try {
            if (contentType.equals("application/vnd.ms-excel") || contentType.equals("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")) {
                // Обработка XLS или XLSX файла
                List<TrackingResultAdd> trackingResults = trackingNumberServiceXLS.processTrackingNumber(file, user);
                model.addAttribute("trackingResults", trackingResults);
            } else if (contentType.startsWith("image/")) {
                System.out.println("я тут");

                // Обработка изображения (OCR)
                String recognizedText = trackNumberOcrService.processImage(file);
                System.out.println("Распознанный текст: " + recognizedText);  // Для дебага

                // Извлечение трек-номеров из текста
                List<TrackingResultAdd> trackingResults = trackNumberOcrService.extractAndProcessTrackingNumbers(recognizedText, user);
                System.out.println("Трек-номера: " + trackingResults);  // Для дебага

                // Добавление результатов в модель
                model.addAttribute("trackingResults", trackingResults);

            } else {
                model.addAttribute("customError", "Неподдерживаемый тип файла. Загрузите XLS, XLSX или изображение.");
                return "home";
            }
        } catch (IOException e) {
            model.addAttribute("generalError", "Ошибка при обработке файла: " + e.getMessage());
        } catch (Exception e) {
            model.addAttribute("generalError", "Ошибка: " + e.getMessage());
        }

        return "home";
    }*/

    @GetMapping("/privacy-policy")
    public String privacyPolicy() {
        return "privacy-policy";
    }

    @GetMapping("/terms-of-use")
    public String termsOfUse() {
        return "terms-of-use";
    }

}