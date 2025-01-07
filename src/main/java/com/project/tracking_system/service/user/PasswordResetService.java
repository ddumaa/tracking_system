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

/**
 * Сервис для восстановления пароля пользователя.
 * <p>
 * Этот сервис управляет процессом восстановления пароля пользователя, включая создание токенов для сброса пароля,
 * отправку ссылок для восстановления через электронную почту и сброс пароля с проверкой действительности токена.
 * </p>
 *
 * @author Dmitriy Anisimov
 * @date Добавленно 07.01.2025
 */
@Service
public class PasswordResetService {

    private final PasswordResetTokenRepository tokenRepository;  // Репозиторий для работы с токенами сброса пароля
    private final UserRepository userRepository;  // Репозиторий для работы с пользователями
    private final EmailService emailService;  // Сервис для отправки email
    private final RandomlyGeneratedString randomStringGenerator;  // Генератор случайных строк для токенов
    private final PasswordEncoder passwordEncoder;  // Кодировщик паролей
    private final HtmlEmailTemplateService htmlEmailTemplateService;  // Сервис для генерации шаблонов email

    private static final String LINK = "https://belivery.by/reset-password?token=";  // Ссылка для восстановления пароля

    /**
     * Конструктор класса {@link PasswordResetService}.
     *
     * @param tokenRepository репозиторий для работы с токенами сброса пароля
     * @param userRepository репозиторий для работы с пользователями
     * @param emailService сервис для отправки email
     * @param randomStringGenerator генератор случайных строк для токенов
     * @param passwordEncoder кодировщик паролей
     * @param htmlEmailTemplateService сервис для генерации шаблонов email
     */
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

    /**
     * Создаёт токен для восстановления пароля и отправляет ссылку на email пользователя.
     * <p>
     * Этот метод генерирует уникальный токен для сброса пароля, сохраняет его в базе данных, устанавливает время
     * его истечения через 1 час и отправляет пользователю ссылку для восстановления пароля на его email.
     * </p>
     *
     * @param email адрес электронной почты пользователя, для которого нужно восстановить пароль
     */
    @Transactional
    public void createPasswordResetToken(String email) {
        userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("Пользователь не найден"));

        String token = randomStringGenerator.generateConfirmCodRegistration();
        String resetLink = LINK + token;
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

    /**
     * Сбрасывает пароль пользователя с использованием токена для восстановления.
     * <p>
     * Этот метод проверяет действительность токена, находит пользователя и обновляет его пароль, после чего
     * удаляет токен из базы данных.
     * </p>
     *
     * @param token токен для сброса пароля
     * @param newPassword новый пароль пользователя
     * @throws IllegalArgumentException если токен недействителен или срок его действия истёк
     */
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

    /**
     * Проверяет, действителен ли токен для сброса пароля.
     * <p>
     * Этот метод проверяет, существует ли токен в базе данных и не истёк ли его срок действия.
     * </p>
     *
     * @param token токен для сброса пароля
     * @return {@code true}, если токен действителен, иначе {@code false}
     */
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
