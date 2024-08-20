package com.project.tracking_system.service;

import com.project.tracking_system.repository.ConfirmationTokenRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class TokenCleanupService {

    private final ConfirmationTokenRepository confirmationTokenRepository;

    @Autowired
    public TokenCleanupService(ConfirmationTokenRepository confirmationTokenRepository) {
        this.confirmationTokenRepository = confirmationTokenRepository;
    }

    // Ежечасное удаление токенов, срок действия которых истек
    @Scheduled(cron = "0 0 * * * *")
    public void cleanupExpiredTokens() {
        LocalDateTime expiryDate = LocalDateTime.now().minusHours(1);
        confirmationTokenRepository.deleteByCreatedAtBefore(expiryDate);
    }
}