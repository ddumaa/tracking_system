package com.project.tracking_system.controller;

import com.project.tracking_system.dto.ContactFormRequest;
import com.project.tracking_system.service.contact.ContactService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
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

    /**
     * Отображает страницу с формой обратной связи.
     *
     * @return имя шаблона страницы контактов
     */
    @GetMapping("/contacts")
    public String contactPage(Model model) {
        // Добавляем пустой объект формы в модель для привязки полей на странице
        model.addAttribute("contactForm", new ContactFormRequest());
        return "marketing/contacts";
    }

    /**
     * Обрабатывает отправку формы обратной связи.
     *
     * @param name  имя отправителя
     * @param email email отправителя
     * @param message текст сообщения
     * @param redirectAttributes контейнер для передачи flash-сообщений
     * @return редирект на страницу контактов
     */
    @PostMapping("/contacts/submit")
    public String submitContactForm(
            @Valid @ModelAttribute("contactForm") ContactFormRequest contactForm,
            BindingResult bindingResult,
            RedirectAttributes redirectAttributes) {
        // Если при валидации формы обнаружены ошибки - возвращаем пользователя на страницу контактов
        if (bindingResult.hasErrors()) {
            return "marketing/contacts";
        }

        // Передаём корректные данные в сервис для дальнейшей обработки
        contactService.processContactRequest(contactForm);
        redirectAttributes.addFlashAttribute("success",
                "Сообщение отправлено! Мы свяжемся с вами в ближайшее время.");
        return "redirect:/contacts";
    }

}