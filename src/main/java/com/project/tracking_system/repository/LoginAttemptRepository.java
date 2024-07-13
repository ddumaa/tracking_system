package com.project.tracking_system.repository;

import com.project.tracking_system.entity.LoginAttempt;
import com.project.tracking_system.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface LoginAttemptRepository extends JpaRepository<LoginAttempt, Long> {
    Optional<LoginAttempt> findByUser(User user);
}
