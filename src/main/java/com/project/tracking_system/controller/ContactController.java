package com.project.tracking_system.controller;

import com.project.tracking_system.service.contact.ContactService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
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

    /**
     * Отображает страницу с формой обратной связи.
     *
     * @return имя шаблона страницы контактов
     */
    @GetMapping("/contacts")
    public String contactPage() {
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
    public String submitContactForm(@RequestParam String name,
                                    @RequestParam String email,
                                    @RequestParam String message,
                                    RedirectAttributes redirectAttributes) {
        contactService.processContactRequest(name, email, message);
        redirectAttributes.addFlashAttribute("success",
                "Сообщение отправлено! Мы свяжемся с вами в ближайшее время.");
        return "redirect:/contacts";
    }

}