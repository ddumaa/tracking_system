package com.project.tracking_system.service.email;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Сервис для отправки HTML email сообщений.
 * <p>
 * Этот сервис используется для отправки email сообщений с HTML-содержимым.
 * Он использует {@link JavaMailSender} для отправки писем через SMTP сервер.
 * </p>
 *
 * @author Dmitriy Anisimov
 * @date 07.01.2025
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private final JavaMailSender emailSender;
    private final HtmlEmailTemplateService templateService;

    @Value("${spring.mail.username}")
    private String senderEmail;

    /**
     * Отправляет письмо с кодом подтверждения.
     *
     * @param to               Email получателя.
     * @param confirmationCode Код подтверждения.
     */
    public void sendConfirmationEmail(String to, String confirmationCode) {
        if (to == null || to.isBlank()) {
            log.warn("⚠ Email получателя пуст, письмо не отправлено.");
            return;
        }

        String htmlContent = templateService.generateEmail("confirmation-email",
                Map.of("to", to, "confirmationCode", confirmationCode));

        sendHtmlEmailAsync(to, "Подтверждение регистрации", htmlContent);
    }

    /**
     * Отправляет письмо для сброса пароля.
     *
     * @param to        Email получателя.
     * @param resetLink Ссылка для сброса пароля.
     */
    public void sendPasswordResetEmail(String to, String resetLink) {
        if (to == null || to.isBlank()) {
            log.warn("⚠ Email получателя пуст, письмо не отправлено.");
            return;
        }

        String htmlContent = templateService.generateEmail("password-reset-email",
                Map.of("to", to, "resetLink", resetLink));

        sendHtmlEmailAsync(to, "Восстановление пароля", htmlContent);
    }

    /**
     * Асинхронно отправляет HTML email сообщение.
     *
     * @param to      адрес получателя.
     * @param subject тема письма.
     * @param content HTML-содержимое письма.
     */
    @Async
    public void sendHtmlEmailAsync(String to, String subject, String content) {
        try {
            MimeMessage message = emailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(senderEmail);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(content, true);

            emailSender.send(message);
            log.info("✅ Письмо успешно отправлено на {}", to);
        } catch (MessagingException e) {
            log.error("❌ Ошибка при отправке письма на {}: {}", to, e.getMessage(), e);
        }
    }
}