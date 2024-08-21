package com.project.tracking_system.service.user;

import com.project.tracking_system.repository.ConfirmationTokenRepository;
import com.project.tracking_system.repository.PasswordResetTokenRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class TokenCleanupService {

    private final ConfirmationTokenRepository confirmationTokenRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;

    @Autowired
    public TokenCleanupService(ConfirmationTokenRepository confirmationTokenRepository,
                               PasswordResetTokenRepository passwordResetTokenRepository) {
        this.confirmationTokenRepository = confirmationTokenRepository;
        this.passwordResetTokenRepository = passwordResetTokenRepository;
    }

    // Ежечасное удаление токенов подтверждения, срок действия которых истек
    @Scheduled(cron = "0 0 * * * *")
    public void cleanupExpiredConfirmationTokens() {
        LocalDateTime expiryDate = LocalDateTime.now().minusHours(1);
        confirmationTokenRepository.deleteByCreatedAtBefore(expiryDate);
    }

    // Ежечасное удаление токенов для восстановления пароля, срок действия которых истек
    @Scheduled(cron = "0 0 * * * *")
    public void cleanupExpiredPasswordResetTokens() {
        LocalDateTime expiryDate = LocalDateTime.now().minusHours(1);
        passwordResetTokenRepository.deleteByExpirationDateBefore(expiryDate);
    }
}