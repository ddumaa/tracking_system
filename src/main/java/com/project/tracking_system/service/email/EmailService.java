package com.project.tracking_system.service.email;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.validator.routines.EmailValidator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;
import com.project.tracking_system.utils.EmailUtils;

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
    /**
     * Исполнитель задач, используемый для асинхронной отправки email.
     * Инъекция позволяет легко заменить реализацию и соответствует принципу
     * инверсии зависимостей.
     */
    private final TaskExecutor emailExecutor;

    @Value("${spring.mail.username}")
    private String senderEmail;

    /**
     * Отправляет письмо с кодом подтверждения.
     *
     * @param to               Email получателя.
     * @param confirmationCode Код подтверждения.
     */
    public void sendConfirmationEmail(String to, String confirmationCode) {
        // Логируем только email без кода подтверждения
        log.info("📨 Генерация email для: {}", EmailUtils.maskEmail(to));

        if (!isValidEmail(to)) {
            log.warn("⚠ Неверный формат email: {}", EmailUtils.maskEmail(to));
            return;
        }

        try {
            String htmlContent = templateService.generateEmail("confirmation-email",
                    Map.of("confirmationCode", confirmationCode));

            log.info("✅ HTML-шаблон успешно сгенерирован, отправляем письмо...");
            sendHtmlEmailAsync(to, "Подтверждение регистрации", htmlContent);
        } catch (Exception e) {
            log.error("❌ Ошибка при генерации email-шаблона: {}", e.getMessage(), e);
        }
    }

    /**
     * Отправляет письмо для сброса пароля.
     *
     * @param to        Email получателя.
     * @param resetLink Ссылка для сброса пароля.
     */
    public void sendPasswordResetEmail(String to, String resetLink) {
        if (!isValidEmail(to)) {
            log.warn("⚠ Неверный формат email: {}", EmailUtils.maskEmail(to));
            return;
        }

        try {
            String htmlContent = templateService.generateEmail("password-reset-email",
                    Map.of("resetLink", resetLink));

            sendHtmlEmailAsync(to, "Восстановление пароля", htmlContent);
        } catch (Exception e) {
            log.error("❌ Ошибка при генерации email-шаблона для сброса пароля: {}", e.getMessage(), e);
        }
    }

    /**
     * Асинхронно отправляет HTML email сообщение.
     * <p>
     * Метод запускается в отдельном потоке из {@code emailExecutor}, что
     * позволяет не блокировать основной поток приложения во время отправки
     * писем.
     * </p>
     *
     * @param to      адрес получателя.
     * @param subject тема письма.
     * @param content HTML-содержимое письма.
     */
    @Async("emailExecutor")
    public void sendHtmlEmailAsync(String to, String subject, String content) {
        log.info("📧 Начинаем отправку email на {}", EmailUtils.maskEmail(to));

        try {
            MimeMessage message = createMimeMessage(to, subject, content);
            emailSender.send(message);
            log.info("✅ Email успешно отправлен на {}", EmailUtils.maskEmail(to));
        } catch (MessagingException e) {
            log.error("❌ Ошибка при отправке email: {}", e.getMessage(), e);
        }
    }

    /**
     * Создает email-сообщение с заданным содержимым.
     */
    private MimeMessage createMimeMessage(String to, String subject, String content) throws MessagingException {
        MimeMessage message = emailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
        helper.setFrom(senderEmail);
        helper.setTo(to);
        helper.setSubject(subject);
        helper.setText(content, true);
        return message;
    }

    /**
     * Проверяет, является ли email корректным.
     */
    private boolean isValidEmail(String email) {
        return email != null && EmailValidator.getInstance().isValid(email);
    }
}