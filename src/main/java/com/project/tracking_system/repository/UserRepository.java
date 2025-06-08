package com.project.tracking_system.repository;

import com.project.tracking_system.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserRepository extends JpaRepository <User, Long> {

    /**
     * Найти пользователя по адресу электронной почты.
     *
     * @param userEmail email пользователя
     * @return найденный пользователь или {@code null}
     */
    Optional<User> findByEmail(String userEmail);

    /**
     * Найти пользователя по идентификатору.
     *
     * @param userId идентификатор пользователя
     * @return найденный пользователь или {@code null}
     */
    Optional<User> findById(Long userId);

    /**
     * Подсчитать количество пользователей по названию их тарифного плана.
     *
     * @param planName название подписки
     * @return число пользователей с таким тарифом
     */
    @Query("SELECT COUNT(u) FROM User u JOIN u.subscription s JOIN s.subscriptionPlan sp WHERE sp.name = :planName")
    long countUsersBySubscriptionPlan(@Param("planName") String planName);
}