package com.project.tracking_system.service.contact;

import com.project.tracking_system.service.email.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Сервис обработки сообщений с формы обратной связи.
 * <p>
 * При получении обращения отправляет письмо на адрес поддержки.
 * </p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ContactService {

    private final EmailService emailService;

    /** Адрес получателя обращений. */
    @Value("${contact.recipient:support@belivery.by}")
    private String recipientEmail;

    /**
     * Обрабатывает обращение пользователя, отправляя его содержимое по почте.
     *
     * @param name    имя отправителя
     * @param email   email отправителя
     * @param message текст обращения
     */
    public void processContactRequest(String name, String email, String message) {
        log.info("Получено обращение от {} <{}>", name, email);
        emailService.sendContactEmail(recipientEmail, Map.of(
                "name", name,
                "email", email,
                "message", message
        ));
    }
}
