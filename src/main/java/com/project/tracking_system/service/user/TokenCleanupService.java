package com.project.tracking_system.service.user;

import com.project.tracking_system.repository.ConfirmationTokenRepository;
import com.project.tracking_system.repository.PasswordResetTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;

/**
 * Сервис для очистки устаревших токенов подтверждения и восстановления пароля.
 * <p>
 * Этот сервис периодически удаляет токены, срок действия которых истек, чтобы освободить пространство в базе данных
 * и предотвратить использование недействительных токенов.
 * </p>
 *
 * @author Dmitriy Anisimov
 * @date Добавленно 07.01.2025
 */
@Service
@RequiredArgsConstructor
public class TokenCleanupService {

    private final ConfirmationTokenRepository confirmationTokenRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;

    /**
     * Удаляет токены, срок действия которых истек.
     * <p>
     * Метод запускается каждый час с помощью планировщика задач.
     * Токены, созданные более чем час назад или истекшие, будут удалены.
     * </p>
     */
    @Transactional
    @Scheduled(cron = "0 0 * * * *")
    public void cleanupExpiredTokens() {
        ZonedDateTime expiryDate = ZonedDateTime.now(ZoneOffset.UTC).minusHours(1);

        // Удаление токенов подтверждения
        confirmationTokenRepository.deleteByCreatedAtBefore(expiryDate);

        // Удаление токенов восстановления пароля
        passwordResetTokenRepository.deleteByExpirationDateBefore(expiryDate);
    }
}