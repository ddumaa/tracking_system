package com.project.tracking_system.service.contact;

import com.project.tracking_system.dto.ContactFormRequest;
import com.project.tracking_system.exception.RateLimitExceededException;
import com.project.tracking_system.service.email.EmailService;
import com.project.tracking_system.service.ratelimit.RateLimiter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Сервис обработки сообщений с формы обратной связи.
 * <p>
 * Перед отправкой обращения сервис проверяет соблюдение лимита запросов,
 * что помогает защитить форму от спама и автоматических рассылок.
 * </p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ContactService {

    /** Сервис отправки email-сообщений. */
    private final EmailService emailService;

    /** Сервис ограничения частоты запросов. */
    private final RateLimiter rateLimiter;

    /** Адрес получателя обращений. */
    @Value("${contact.recipient:support@belivery.by}")
    private String recipientEmail;

    /**
     * Обрабатывает обращение пользователя, отправляя его содержимое по почте.
     *
     * @param request объект с данными обращения пользователя
     * @param ip      IP-адрес отправителя для проверки лимита
     * @throws RateLimitExceededException если лимит обращений превышен
     */
    public void processContactRequest(ContactFormRequest request, String ip) {
        if (!rateLimiter.isAllowed(ip)) {
            throw new RateLimitExceededException("Превышен лимит обращений для IP: " + ip);
        }
        log.info("Получено обращение от {} <{}>", request.getName(), request.getEmail());
        emailService.sendContactEmail(recipientEmail, Map.of(
                "name", request.getName(),
                "email", request.getEmail(),
                "message", request.getMessage()
        ));
    }
}
