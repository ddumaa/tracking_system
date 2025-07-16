package com.project.tracking_system.service.user;

import com.project.tracking_system.entity.PasswordResetToken;
import com.project.tracking_system.entity.User;
import com.project.tracking_system.repository.PasswordResetTokenRepository;
import com.project.tracking_system.repository.UserRepository;
import com.project.tracking_system.service.email.EmailService;
import com.project.tracking_system.utils.EmailUtils;
import com.project.tracking_system.utils.TokenUtils;
import com.project.tracking_system.utils.HashUtils;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
@Slf4j
@RequiredArgsConstructor
public class PasswordResetService {

    private final PasswordResetTokenRepository tokenRepository;  // Репозиторий для работы с токенами сброса пароля
    private final UserRepository userRepository;  // Репозиторий для работы с пользователями
    private final EmailService emailService;  // Сервис для отправки email
    private final RandomlyGeneratedString randomStringGenerator;  // Генератор случайных строк для токенов
    private final PasswordEncoder passwordEncoder;  // Кодировщик паролей

    private static final String LINK = "https://belivery.by/reset-password?token=";  // Ссылка для восстановления пароля

    /**
     * Создаёт токен для восстановления пароля и отправляет ссылку на email пользователя.
     * <p>
     * Этот метод генерирует уникальный токен для сброса пароля, сохраняет в базе данных его хеш,
     * устанавливает время истечения через 1 час и отправляет пользователю ссылку для восстановления пароля.
     * </p>
     *
     * @param email адрес электронной почты пользователя, для которого нужно восстановить пароль
     */
    @Transactional
    public void createPasswordResetToken(String email) {
        log.info("Начало процесса генерации токена сброса пароля для {}", EmailUtils.maskEmail(email));

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> {
                    log.warn("❌ Пользователь с email {} не найден", EmailUtils.maskEmail(email));
                    return new UsernameNotFoundException("Пользователь с email " + email + " не найден");
                });

        log.info("✅ Пользователь {} найден. Генерируем токен...", EmailUtils.maskEmail(user.getEmail()));

        String token = randomStringGenerator.generateConfirmationCode();
        String hashedToken = HashUtils.sha256(token);
        String resetLink = LINK + token;

        // Не выводим значение токена в лог по соображениям безопасности
        log.debug("🔑 Сгенерирован токен для email {}", EmailUtils.maskEmail(email));

        saveOrUpdatePasswordResetToken(email, hashedToken);

        log.info("📧 Отправка email для сброса пароля пользователю {}", EmailUtils.maskEmail(email));
        emailService.sendPasswordResetEmail(email, resetLink);
        log.info("Процесс генерации токена для {} успешно завершён", EmailUtils.maskEmail(email));
    }

    /**
     * Сохраняет или обновляет хеш токена для сброса пароля.
     * <p>
     * В базе данных хранится только хеш токена, чтобы исключить его утечку.
     * </p>
     */
    private void saveOrUpdatePasswordResetToken(String email, String hashedToken) {
        tokenRepository.findByEmail(email)
                .ifPresentOrElse(
                        existingToken -> {
                            log.info("♻️ Обновление существующего токена для email {}", EmailUtils.maskEmail(email));
                            existingToken.setToken(hashedToken);
                            existingToken.setExpirationDate(ZonedDateTime.now(ZoneOffset.UTC).plusHours(1));
                            tokenRepository.save(existingToken);
                        },
                        () -> {
                            log.info("🆕 Создание нового токена для email {}", EmailUtils.maskEmail(email));
                            PasswordResetToken newToken = new PasswordResetToken(email, hashedToken);
                            tokenRepository.save(newToken);
                        }
                );
    }

    /**
     * Сбрасывает пароль пользователя с использованием токена для восстановления.
     * <p>
     * Этот метод проверяет действительность токена, находит пользователя и обновляет его пароль, после чего
     * удаляет токен из базы данных. Полный токен в логах не выводится, чтобы предотвратить его утечку.
     * </p>
     *
     * @param token токен для сброса пароля
     * @param newPassword новый пароль пользователя
     * @throws IllegalArgumentException если токен недействителен или срок его действия истёк
     */
    @Transactional
    public void resetPassword(String token, String newPassword) {
        // Логируем только первые символы токена из соображений безопасности
        log.info("Начало сброса пароля по токену {}", TokenUtils.maskToken(token));
        if (!isTokenValid(token)) {
            throw new IllegalArgumentException("Срок действия токена истек");
        }

        String hashedToken = HashUtils.sha256(token);
        PasswordResetToken resetToken = tokenRepository.findByToken(hashedToken)
                .orElseThrow(() -> new IllegalArgumentException("Недействительный токен"));

        String email = resetToken.getEmail();

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("Пользователь не найден"));

        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        tokenRepository.deleteByToken(hashedToken);

        log.info("Пароль для пользователя {} успешно сброшен", EmailUtils.maskEmail(email));
    }

    /**
     * Проверяет, действителен ли токен для сброса пароля.
     * <p>
     * Этот метод проверяет, существует ли хеш токена в базе данных и не истёк ли его срок действия.
     * </p>
     *
     * @param token токен для сброса пароля
     * @return {@code true}, если токен действителен, иначе {@code false}
     */
    public boolean isTokenValid(String token) {
        String hashedToken = HashUtils.sha256(token);
        Optional<PasswordResetToken> resetToken = tokenRepository.findByToken(hashedToken);
        if (resetToken.isPresent()) {
            PasswordResetToken tokenEntity = resetToken.get();

            // Текущее время в UTC
            ZonedDateTime now = ZonedDateTime.now(ZoneOffset.UTC);
            // Время истечения токена в UTC
            ZonedDateTime expiration = tokenEntity.getExpirationDate();
            return expiration.isAfter(now);
        }
        log.warn("Токен не найден в базе данных");
        return false;
    }
}

