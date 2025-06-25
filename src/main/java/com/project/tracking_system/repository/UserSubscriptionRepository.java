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
     * Получение кода подписки пользователя.
     *
     * @param userId ID пользователя
     * @return код подписки (например, FREE, PREMIUM)
     */
    @Query("SELECT sp.code FROM UserSubscription s JOIN s.subscriptionPlan sp WHERE s.user.id = :userId")
    String getSubscriptionPlanCode(@Param("userId") Long userId);

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

    /**
     * Получить все подписки вместе с пользователем и планом.
     *
     * @return список подписок
     */
    @Query("SELECT s FROM UserSubscription s JOIN FETCH s.user u JOIN FETCH s.subscriptionPlan sp")
    List<UserSubscription> findAllWithUserAndPlan();

    /**
     * Найти идентификаторы пользователей, у которых включена указанная функция.
     *
     * @param key ключ функции
     * @return список идентификаторов пользователей
     */
    @Query("""
        SELECT us.user.id FROM UserSubscription us
        JOIN us.subscriptionPlan sp
        JOIN sp.features f
        WHERE f.featureKey = :key AND f.enabled = true AND us.autoUpdateEnabled = true
        """)
    List<Long> findUserIdsByFeature(@Param("key") com.project.tracking_system.model.subscription.FeatureKey key);

    /**
     * Проверить, включено ли автообновление треков у пользователя.
     *
     * @param userId идентификатор пользователя
     * @return {@code true}, если автообновление разрешено
     */
    @Query("SELECT us.autoUpdateEnabled FROM UserSubscription us WHERE us.user.id = :userId")
    boolean isAutoUpdateEnabled(@Param("userId") Long userId);

    /**
     * Обновить флаг автообновления треков.
     *
     * @param userId идентификатор пользователя
     * @param enabled новое значение флага
     * @return количество обновлённых записей
     */
    @Modifying
    @Transactional
    @Query("""
        UPDATE UserSubscription us
        SET us.autoUpdateEnabled = :enabled
        WHERE us.user.id = :userId
        """)
    int updateAutoUpdateEnabled(@Param("userId") Long userId, @Param("enabled") boolean enabled);

}