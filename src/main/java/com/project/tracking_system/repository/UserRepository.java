package com.project.tracking_system.repository;

import com.project.tracking_system.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserRepository extends JpaRepository <User, Long> {

    Optional<User> findByEmail(String userEmail);

    Optional<User> findById(Long userId);

    @Query("SELECT COUNT(u) FROM User u JOIN u.subscription s JOIN s.subscriptionPlan sp WHERE sp.name = :planName")
    long countUsersBySubscriptionPlan(@Param("planName") String planName);
}