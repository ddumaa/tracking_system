package com.project.tracking_system.repository;

import com.project.tracking_system.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository <User, Long> {

    Optional<User> findByEmail(String userEmail);

    Optional<User> findById(Long userId);

    // Поиск пользователей по подписке (FREE, PREMIUM)
    @Query("SELECT u FROM User u WHERE u.subscriptionPlan.name = :planName")
    List<User> findUsersBySubscription(@Param("planName") String planName);

    // Проверяем, является ли пользователь PREMIUM
    @Query("SELECT COUNT(u) > 0 FROM User u WHERE u.id = :userId AND u.subscriptionPlan.name = :planName")
    boolean isUserSubscribedToPlan(@Param("userId") Long userId, @Param("planName") String planName);

    @Query("SELECT u FROM User u WHERE u.subscriptionEndDate IS NOT NULL AND u.subscriptionEndDate < :now")
    List<User> findUsersWithExpiredSubscription(@Param("now") ZonedDateTime now);

    @Query("SELECT u.updateCount FROM User u WHERE u.id = :userId")
    int getUpdateCount(@Param("userId") Long userId);

    @Query("SELECT u.lastUpdateDate FROM User u WHERE u.id = :userId")
    ZonedDateTime getLastUpdateDate(@Param("userId") Long userId);

    @Modifying
    @Query("UPDATE User u SET u.updateCount = 0, u.lastUpdateDate = :date WHERE u.id = :userId")
    void resetUpdateCount(@Param("userId") Long userId, @Param("date") ZonedDateTime date);

    @Modifying
    @Query("UPDATE User u SET u.updateCount = u.updateCount + :count, u.lastUpdateDate = :date WHERE u.id = :userId")
    void incrementUpdateCount(@Param("userId") Long userId, @Param("count") int count, @Param("date") ZonedDateTime date);

    @Query("SELECT u.useCustomCredentials FROM User u WHERE u.id = :userId")
    boolean isUsingCustomCredentials(@Param("userId") Long userId);

    @Query("SELECT u.updateCount, u.lastUpdateDate FROM User u WHERE u.id = :userId")
    Object[] getUpdateCountAndLastUpdateDate(@Param("userId") Long userId);

    @Query("SELECT sp.name FROM User u JOIN u.subscriptionPlan sp WHERE u.id = :userId")
    String getSubscriptionPlanName(@Param("userId") Long userId);

    // Подсчёт пользователей по подписке
    @Query("SELECT COUNT(u) FROM User u WHERE u.subscriptionPlan.name = :planName")
    long countUsersBySubscriptionPlan(@Param("planName") String planName);
}