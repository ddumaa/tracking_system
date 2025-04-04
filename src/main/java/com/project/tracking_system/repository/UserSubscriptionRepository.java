package com.project.tracking_system.repository;

import com.project.tracking_system.dto.UpdateInfoDto;
import com.project.tracking_system.entity.UserSubscription;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

/**
 * @author Dmitriy Anisimov
 * @date 10.03.2025
 */
public interface UserSubscriptionRepository extends JpaRepository<UserSubscription, Long> {

    /**
     * Получение подписки по ID пользователя.
     */
    Optional<UserSubscription> findByUserId(Long userId);

    /**
     * Найти пользователей с истекшей подпиской.
     */
    @Query("""
        SELECT s FROM UserSubscription s 
        WHERE s.subscriptionEndDate IS NOT NULL 
          AND s.subscriptionEndDate < :now
        """)
    List<UserSubscription> findExpiredSubscriptions(@Param("now") ZonedDateTime now);

    /**
     * Инкремент количества обновлений треков.
     */
    @Modifying
    @Transactional
    @Query("""
       UPDATE UserSubscription s 
       SET s.updateCount = s.updateCount + :count, 
           s.resetDate = :date 
       WHERE s.user.id = :userId
       """)
    void incrementUpdateCount(@Param("userId") Long userId, @Param("count") int count, @Param("date") LocalDate date);

    /**
     * Получение информации об использовании лимитов.
     */
    @Query("SELECT new com.project.tracking_system.dto.UpdateInfoDto(s.updateCount, s.resetDate) " +
            "FROM UserSubscription s WHERE s.user.id = :userId")
    UpdateInfoDto getUpdateInfo(@Param("userId") Long userId);

    /**
     * Получение названия подписки пользователя.
     */
    @Query("SELECT sp.name FROM UserSubscription s JOIN s.subscriptionPlan sp WHERE s.user.id = :userId")
    String getSubscriptionPlanName(@Param("userId") Long userId);

    /**
     * Сброс количества обновлений (лимитов).
     */
    @Modifying
    @Transactional
    @Query("""
       UPDATE UserSubscription s 
       SET s.updateCount = 0, s.resetDate = :resetDate 
       WHERE s.user.id = :userId
       """)
    void resetUpdateCount(@Param("userId") Long userId, @Param("resetDate") LocalDate resetDate);

}