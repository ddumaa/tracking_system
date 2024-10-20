package com.project.tracking_system.repository;

import com.project.tracking_system.entity.PasswordResetToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.ZonedDateTime;
import java.util.Optional;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {

    Optional<PasswordResetToken> findByToken(String token);

    Optional<PasswordResetToken> findByEmail(String email);

    void deleteByToken(String token);
    void deleteByExpirationDateBefore(ZonedDateTime expiryDate);
}