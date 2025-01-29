package com.project.tracking_system.service.email;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

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
public class EmailService {

    private final JavaMailSender emailSender;

    @Autowired
    public EmailService(JavaMailSender emailSender) {
        this.emailSender = emailSender;
    }

    /**
     * Отправляет HTML email сообщение.
     * <p>
     * Этот метод используется для отправки email с заданным адресом получателя, темой и HTML-содержимым.
     * </p>
     *
     * @param to      адрес получателя.
     * @param subject тема письма.
     * @param content HTML-содержимое письма.
     * @throws MessagingException если произошла ошибка при отправке сообщения.
     */
    public void sendHtmlEmail(String to, String subject, String content) throws MessagingException {
        MimeMessage message = emailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

        helper.setTo(to);
        helper.setSubject(subject);
        helper.setText(content, true);

        emailSender.send(message);
    }
}