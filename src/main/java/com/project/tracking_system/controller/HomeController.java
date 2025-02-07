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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private final static Logger logger = LoggerFactory.getLogger(HomeController.class);

    private final UserService userService;
    private final TrackParcelService trackParcelService;
    private final TypeDefinitionTrackPostService typeDefinitionTrackPostService;
    private final LoginAttemptService loginAttemptService;
    private final PasswordResetService passwordResetService;
    private final TrackingNumberServiceXLS trackingNumberServiceXLS;
    private final TrackNumberOcrService trackNumberOcrService;

    @Autowired
    public HomeController(UserService userService, TrackParcelService trackParcelService,
                          TypeDefinitionTrackPostService typeDefinitionTrackPostService,
                          LoginAttemptService loginAttemptService,
                          PasswordResetService passwordResetService,
                          TrackingNumberServiceXLS trackingNumberServiceXLS,
                          TrackNumberOcrService trackNumberOcrService) {
        this.userService = userService;
        this.trackParcelService = trackParcelService;
        this.typeDefinitionTrackPostService = typeDefinitionTrackPostService;
        this.loginAttemptService = loginAttemptService;
        this.passwordResetService = passwordResetService;
        this.trackingNumberServiceXLS = trackingNumberServiceXLS;
        this.trackNumberOcrService = trackNumberOcrService;
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
    public String home(@ModelAttribute("number") String number, Model model) {
        // Получаем аутентификацию текущего пользователя
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication != null && authentication.isAuthenticated() && !(authentication instanceof AnonymousAuthenticationToken)) {
            // Получаем кастомного пользователя
            User user = (User) authentication.getPrincipal();

            logger.info("Получен запрос на обновление посылки для пользователя: {}", user.getEmail());

            model.addAttribute("authenticatedUser", user.getEmail());
            model.addAttribute("number", number);

            try {
                // Получаем данные посылки
                TrackInfoListDTO trackInfo = typeDefinitionTrackPostService.getTypeDefinitionTrackPostService(user, number);
                model.addAttribute("trackInfo", trackInfo);

                // Если данные пустые, отображаем сообщение об ошибке
                if (trackInfo.getList().isEmpty()) {
                    model.addAttribute("customError", "Нет данных для указанного номера посылки.");
                }

                // Сохраняем данные, если посылка была найдена
                trackParcelService.save(number, trackInfo, user);
                logger.debug("Данные посылки сохранены для пользователя: {}", user.getEmail());

            } catch (IllegalArgumentException e) {
                model.addAttribute("customError", e.getMessage());
                logger.error("Ошибка при получении данных посылки: {}", e.getMessage());
            } catch (Exception e) {
                model.addAttribute("generalError", "Произошла ошибка при обработке запроса.");
                logger.error("Общая ошибка при обработке запроса: {}", e.getMessage());
            }

        } else {
            model.addAttribute("error", "Пожалуйста, авторизуйтесь.");
            logger.warn("Попытка доступа к посылке неаутентифицированным пользователем.");
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
    @PostMapping("/upload")
    public String uploadFile(@RequestParam("file") MultipartFile file, Model model) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        User user = null;

        // Проверка аутентификации и извлечение кастомного пользователя
        if (auth != null && auth.isAuthenticated() && !(auth instanceof AnonymousAuthenticationToken)) {
            user = (User) auth.getPrincipal(); // Прямо извлекаем кастомного пользователя
            model.addAttribute("authenticatedUser", user.getEmail());
        } else {
            model.addAttribute("authenticatedUser", null);
        }

        // Проверка, что файл не пустой
        if (file.isEmpty()) {
            model.addAttribute("customError", "Пожалуйста, выберите XLS, XLSX или изображение для загрузки.");
            return "home";
        }

        // Определение MIME-типа файла
        String contentType = file.getContentType();
        if (contentType == null) {
            model.addAttribute("customError", "Не удалось определить тип файла.");
            return "home";
        }

        try {
            // Обработка различных типов файлов
            if (contentType.equals("application/vnd.ms-excel") || contentType.equals("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")) {
                // Обработка XLS или XLSX файла
                List<TrackingResultAdd> trackingResults = trackingNumberServiceXLS.processTrackingNumber(file, user);
                model.addAttribute("trackingResults", trackingResults);
            } else if (contentType.startsWith("image/")) {
                // Обработка изображений (например, OCR, извлечение текста с изображений)
                // Пример: используем сервис для обработки изображений
                 String recognizedText = trackNumberOcrService.processImage(file);
                 List<TrackingResultAdd> trackingResults = trackNumberOcrService.extractAndProcessTrackingNumbers(recognizedText, user);
                 model.addAttribute("trackingResults", trackingResults);

                model.addAttribute("customError", "OCR не реализован в текущей версии.");
                return "home";
            } else {
                model.addAttribute("customError", "Неподдерживаемый тип файла. Загрузите XLS, XLSX или изображение.");
                return "home";
            }
        } catch (IOException e) {
            // Логирование и обработка ошибок ввода-вывода
            model.addAttribute("generalError", "Ошибка при обработке файла: " + e.getMessage());
            logger.error("IOException при обработке файла: " + e.getMessage(), e);
        } catch (Exception e) {
            // Логирование других ошибок
            model.addAttribute("generalError", "Ошибка: " + e.getMessage());
            logger.error("Ошибка при обработке файла: " + e.getMessage(), e);
        }

        return "home";
    }

    @GetMapping("/privacy-policy")
    public String privacyPolicy() {
        return "privacy-policy";
    }

    @GetMapping("/terms-of-use")
    public String termsOfUse() {
        return "terms-of-use";
    }

}