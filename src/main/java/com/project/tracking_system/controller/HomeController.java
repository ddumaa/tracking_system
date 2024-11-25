package com.project.tracking_system.controller;

import com.project.tracking_system.dto.PasswordResetDTO;
import com.project.tracking_system.dto.TrackingResultAdd;
import com.project.tracking_system.dto.UserRegistrationDTO;
import com.project.tracking_system.dto.TrackInfoListDTO;
import com.project.tracking_system.entity.User;
import com.project.tracking_system.exception.UserAlreadyExistsException;
import com.project.tracking_system.service.OcrService;
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

@Controller
@RequestMapping("/")
public class HomeController {

    private final UserService userService;
    private final TrackParcelService trackParcelService;
    private final TypeDefinitionTrackPostService typeDefinitionTrackPostService;
    private final LoginAttemptService loginAttemptService;
    private final PasswordResetService passwordResetService;
    private final TrackingNumberServiceXLS trackingNumberServiceXLS;
    private final OcrService ocrService;

    @Autowired
    public HomeController(UserService userService, TrackParcelService trackParcelService,
                          TypeDefinitionTrackPostService typeDefinitionTrackPostService,
                          LoginAttemptService loginAttemptService,
                          PasswordResetService passwordResetService,
                          TrackingNumberServiceXLS trackingNumberServiceXLS, OcrService ocrService) {
        this.userService = userService;
        this.trackParcelService = trackParcelService;
        this.typeDefinitionTrackPostService = typeDefinitionTrackPostService;
        this.loginAttemptService = loginAttemptService;
        this.passwordResetService = passwordResetService;
        this.trackingNumberServiceXLS = trackingNumberServiceXLS;
        this.ocrService = ocrService;
    }

    @GetMapping
    public String home() {
        return "home";
    }

    @PostMapping
    public String home(@ModelAttribute("number") String number, Model model, HttpServletRequest request) {

        HttpSession session = request.getSession();
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String authUserName = auth != null && auth.isAuthenticated() && !(auth instanceof AnonymousAuthenticationToken) ? auth.getName() : null;

        model.addAttribute("number", number);

        try {
            TrackInfoListDTO trackInfo = typeDefinitionTrackPostService.getTypeDefinitionTrackPostService(number);
            model.addAttribute("trackInfo", trackInfo);

            if (trackInfo.getList().isEmpty()) {
                model.addAttribute("customError", "Нет данных для указанного номера посылки.");
            }

            if (auth != null && auth.isAuthenticated() && !(auth instanceof AnonymousAuthenticationToken)) {
                model.addAttribute("authenticatedUser", authUserName);
                session.setAttribute("userSession", authUserName);
                trackParcelService.save(number, trackInfo, authUserName);
            } else {
                session.removeAttribute("userSession");
                model.addAttribute("authenticatedUser", null);
            }
            return "home";

        } catch (IllegalArgumentException e) {
            model.addAttribute("customError", e.getMessage());
            return "home";
        } catch (Exception e) {
            model.addAttribute("generalError", "Произошла ошибка при обработке запроса.");
            return "home";
        }

    }

    @GetMapping("/registration")
    public String registration(@ModelAttribute("userDTO") UserRegistrationDTO userRegistrationDTO, Model model) {
        model.addAttribute("userDTO", new UserRegistrationDTO());
        return "registration";
    }

    @PostMapping("/registration")
    public String registration(@Valid @ModelAttribute("userDTO") UserRegistrationDTO userDTO, BindingResult result, Model model) {
        // Проверка, на каком этапе находится процесс регистрации
        boolean isInitialRegistration = userDTO.getConfirmCodRegistration() == null || userDTO.getConfirmCodRegistration().isEmpty();

        // Первый этап регистрации: проверка email и паролей
        if (isInitialRegistration) {
            if (result.hasFieldErrors("email") || result.hasFieldErrors("password") || result.hasFieldErrors("confirmPassword")) {
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

    @GetMapping("/login")
    public String loginPage(@RequestParam(value = "error", required = false) String error,
                            @RequestParam(value = "blocked", required = false) String blocked,
                            HttpSession session, Model model, Principal principal) {
        if (principal != null) {
            return "redirect:/";
        }
        String email = (String) session.getAttribute("email");
        if (blocked != null && email != null) {
            ZonedDateTime unlockTime = loginAttemptService.getUnlockTime(email);
            model.addAttribute("blockedMessage", "Ваш аккаунт заблокирован из-за превышения количества попыток входа. Попробуйте снова после: " + unlockTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        }
        if (error != null && email != null) {
            int remainingAttempts = loginAttemptService.getRemainingAttempts(email);
            model.addAttribute("remainingAttempts", remainingAttempts);
        }
        return "login";
    }

    @GetMapping("/forgot-password")
    public String showForgotPasswordForm(Model model) {
        model.addAttribute("email", "");
        return "forgot-password";
    }

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

    @GetMapping("/reset-password")
    public String showResetPasswordForm(@RequestParam String token, @ModelAttribute("passwordResetDTO") PasswordResetDTO passwordResetDTO, Model model) {
        if (!passwordResetService.isTokenValid(token)) {
            model.addAttribute("error", "Неверный или просроченный токен.");
            return "forgot-password";
        }
        model.addAttribute("token", token);
        return "reset-password";
    }

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

    @PostMapping("/upload")
    public String uploadFile(@RequestParam("file") MultipartFile file, Model model) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String authUserName = (auth != null && auth.isAuthenticated() && !(auth instanceof AnonymousAuthenticationToken))
                ? auth.getName() : null;

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
                List<TrackingResultAdd> trackingResults = trackingNumberServiceXLS.processTrackingNumber(file, authUserName);
                model.addAttribute("trackingResults", trackingResults);
            } else if (contentType.startsWith("image/")) {
                // Обработка изображения (OCR)
                String recognizedText = ocrService.processImage(file);
                // Извлечение трек-номеров из текста
                List<TrackInfoListDTO> trackInfoList = ocrService.extractAndProcessTrackingNumbers(recognizedText);
                model.addAttribute("trackingResults", trackInfoList);
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
    }

}