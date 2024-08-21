package com.project.tracking_system.repository;

import com.project.tracking_system.entity.ConfirmationToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Optional;

public interface ConfirmationTokenRepository extends JpaRepository<ConfirmationToken, Long> {

    Optional<ConfirmationToken> findByEmail(String email);
    void deleteByEmail(String email);
    void deleteByCreatedAtBefore(LocalDateTime expiryDate);

}