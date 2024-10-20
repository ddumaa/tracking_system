package com.project.tracking_system.service.user;

import com.project.tracking_system.entity.PasswordResetToken;
import com.project.tracking_system.entity.User;
import com.project.tracking_system.repository.PasswordResetTokenRepository;
import com.project.tracking_system.repository.UserRepository;
import com.project.tracking_system.service.email.EmailService;
import jakarta.mail.MessagingException;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Optional;

@Service
public class PasswordResetService {

    private final PasswordResetTokenRepository tokenRepository;
    private final UserRepository userRepository;
    private final EmailService emailService;
    private final RandomlyGeneratedString randomStringGenerator;
    private final PasswordEncoder passwordEncoder;
    private final HtmlEmailTemplateService htmlEmailTemplateService;

    @Autowired
    public PasswordResetService(PasswordResetTokenRepository tokenRepository,
                                UserRepository userRepository,
                                EmailService emailService,
                                RandomlyGeneratedString randomStringGenerator,
                                PasswordEncoder passwordEncoder,
                                HtmlEmailTemplateService htmlEmailTemplateService) {
        this.tokenRepository = tokenRepository;
        this.userRepository = userRepository;
        this.emailService = emailService;
        this.randomStringGenerator = randomStringGenerator;
        this.passwordEncoder = passwordEncoder;
        this.htmlEmailTemplateService = htmlEmailTemplateService;
    }

    @Transactional
    public void createPasswordResetToken(String email) {
        userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("Пользователь не найден"));

        String token = randomStringGenerator.generateConfirmCodRegistration();
        String resetLink = "https://belivery.by/reset-password?token=" + token;
        String emailContent = htmlEmailTemplateService.generatePasswordResetEmail(resetLink);

        Optional<PasswordResetToken> byEmail = tokenRepository.findByEmail(email);
        if (byEmail.isPresent()) {
            PasswordResetToken passwordResetToken = byEmail.get();
            passwordResetToken.setToken(token);
            passwordResetToken.setExpirationDate(ZonedDateTime.now(ZoneOffset.UTC).plusHours(1));
            tokenRepository.save(passwordResetToken);
        } else {
            PasswordResetToken resetToken = new PasswordResetToken(email, token);
            tokenRepository.save(resetToken);
        }
        try {
            emailService.sendHtmlEmail(email, "Восстановление пароля", emailContent);
        } catch (MessagingException e) {
            throw new RuntimeException("Ошибка при отправке email", e);
        }
    }

    @Transactional
    public void resetPassword(String token, String newPassword) {
        if (!isTokenValid(token)) {
            throw new IllegalArgumentException("Срок действия токена истек");
        }

        PasswordResetToken resetToken = tokenRepository.findByToken(token)
                .orElseThrow(() -> new IllegalArgumentException("Недействительный токен"));

        String email = resetToken.getEmail();

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("Пользователь не найден"));

        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        tokenRepository.deleteByToken(token);
    }

    public boolean isTokenValid(String token) {
        Optional<PasswordResetToken> resetToken = tokenRepository.findByToken(token);
        if (resetToken.isPresent()) {
            PasswordResetToken tokenEntity = resetToken.get();

            // Текущее время в UTC
            ZonedDateTime now = ZonedDateTime.now(ZoneOffset.UTC);
            // Время истечения токена в UTC
            ZonedDateTime expiration = tokenEntity.getExpirationDate();
            return expiration.isAfter(now);
        }
        System.out.println("Токен не найден");
        return false;
    }

}