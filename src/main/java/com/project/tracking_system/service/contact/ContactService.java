package com.project.tracking_system.service.contact;

import com.project.tracking_system.dto.ContactFormRequest;
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
     * @param request объект с данными обращения пользователя
     */
    public void processContactRequest(ContactFormRequest request) {
        log.info("Получено обращение от {} <{}>", request.getName(), request.getEmail());
        emailService.sendContactEmail(recipientEmail, Map.of(
                "name", request.getName(),
                "email", request.getEmail(),
                "message", request.getMessage()
        ));
    }
}
