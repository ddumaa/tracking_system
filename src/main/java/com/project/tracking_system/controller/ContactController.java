package com.project.tracking_system.controller;

import com.project.tracking_system.dto.ContactFormRequest;
import com.project.tracking_system.exception.RateLimitExceededException;
import com.project.tracking_system.service.captcha.CaptchaService;
import com.project.tracking_system.service.contact.ContactService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Контроллер страницы "Контакты".
 * <p>
 * Отображает форму обратной связи и обрабатывает её отправку.
 * </p>
 */
@RequiredArgsConstructor
@Controller
public class ContactController {

    /** Сервис обработки сообщений обратной связи. */
    private final ContactService contactService;

    /** Сервис проверки капчи. */
    private final CaptchaService captchaService;

    /**
     * Отображает страницу с формой обратной связи.
     *
     * @return имя шаблона страницы контактов
     */
    @GetMapping("/contacts")
    public String contactPage(Model model, HttpServletRequest request) {
        // Подготавливаем модель для отображения формы
        model.addAttribute("contactForm", new ContactFormRequest());
        prepareContactPageModel(model, request);
        return "marketing/contacts";
    }

    /**
     * Обрабатывает отправку формы обратной связи.
     *
     * @param contactForm данные формы от пользователя
     * @param captchaToken токен подтверждения reCAPTCHA
     * @param request HTTP-запрос для получения IP-адреса
     * @param redirectAttributes контейнер для передачи flash-сообщений
     * @return редирект на страницу контактов или повторное отображение формы
     */
    @PostMapping("/contacts/submit")
    public String submitContactForm(
            @Valid @ModelAttribute("contactForm") ContactFormRequest contactForm,
            BindingResult bindingResult,
            @RequestParam(value = "g-recaptcha-response", required = false) String captchaToken,
            HttpServletRequest request,
            Model model,
            RedirectAttributes redirectAttributes) {

        // IP-адрес клиента используется для проверки капчи
        String ip = request.getRemoteAddr();

        // Проверяем токен только при его наличии, чтобы избежать лишних
        // обращений к сервису и упростить тестирование контроллера
        boolean captchaValid = captchaToken != null
                && !captchaToken.isBlank()
                && captchaService.verifyToken(captchaToken, ip);

        // При отсутствии токена или неверной капче добавляем ошибку валидации
        if (!captchaValid) {
            bindingResult.reject("captcha.invalid", "Подтвердите, что вы не робот.");
        }

        // Если при валидации формы обнаружены ошибки - возвращаем пользователя на страницу контактов
        if (bindingResult.hasErrors()) {
            // Повторно подготавливаем модель при ошибках
            prepareContactPageModel(model, request);
            return "marketing/contacts";
        }

        try {
            // Передаём корректные данные в сервис для дальнейшей обработки
            contactService.processContactRequest(contactForm, ip);
        } catch (RateLimitExceededException ex) {
            bindingResult.reject("rate.limit", "Слишком много запросов. Попробуйте позже.");
            prepareContactPageModel(model, request);
            return "marketing/contacts";
        }

        redirectAttributes.addFlashAttribute("success",
                "Сообщение отправлено! Мы свяжемся с вами в ближайшее время.");
        return "redirect:/contacts";
    }

    /**
     * Заполняет модель данными, необходимыми для отображения страницы контактов.
     * <p>
     * Добавляет ключ сайта reCAPTCHA и nonce для прохождения проверки CSP.
     * </p>
     *
     * @param model   модель представления
     * @param request текущий HTTP-запрос
     */
    private void prepareContactPageModel(Model model, HttpServletRequest request) {
        model.addAttribute("recaptchaSiteKey", captchaService.getSiteKey());
        model.addAttribute("nonce", request.getAttribute("nonce"));
    }

}
