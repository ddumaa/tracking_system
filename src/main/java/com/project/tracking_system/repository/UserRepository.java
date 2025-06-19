package com.project.tracking_system.repository;

import com.project.tracking_system.entity.Role;
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

    /**
     * Найти пользователей по фильтрам email, роли и названию подписки.
     *
     * @param search       часть email пользователя
     * @param role         роль пользователя
     * @param subscription название подписки
     * @return список подходящих пользователей
     */
    @Query("""
        SELECT u FROM User u
        LEFT JOIN u.subscription us
        LEFT JOIN us.subscriptionPlan sp
        WHERE (:search = '' OR u.email LIKE CONCAT('%', :search, '%'))
          AND (:role IS NULL OR u.role = :role)
          AND (:subscription IS NULL OR sp.name = :subscription)
        """)
    java.util.List<User> findByFilters(@Param("search") String search,
                                       @Param("role") Role role,
                                       @Param("subscription") String subscription);
}