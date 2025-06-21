package com.project.tracking_system.repository;

import com.project.tracking_system.entity.Role;
import com.project.tracking_system.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
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
     * Подсчитать количество пользователей по коду их тарифного плана.
     *
     * @param planCode код подписки (FREE, PREMIUM)
     * @return число пользователей с таким тарифом
     */
    long countBySubscription_SubscriptionPlan_Code(String code);

    /**
     * Подсчитать пользователей с платными тарифами.
     *
     * @param price минимальная цена тарифа
     * @return количество пользователей с тарифом дороже указанной цены
     */
    long countBySubscription_SubscriptionPlan_PriceGreaterThan(java.math.BigDecimal price);

    /**
     * Найти пользователей по фильтрам: часть email, роль и код подписки.
     *
     * @param search       часть email пользователя
     * @param role         роль пользователя
     * @param subscription код подписки (FREE, PREMIUM)
     * @return список подходящих пользователей
     */
    @Query("""
    SELECT u FROM User u
    LEFT JOIN u.subscription us
    LEFT JOIN us.subscriptionPlan sp
    WHERE (:search = '' OR u.email LIKE CONCAT('%', :search, '%'))
      AND (:role IS NULL OR u.role = :role)
      AND (:subscription IS NULL OR sp.code = :subscription)
    """)
    List<User> findByFilters(@Param("search") String search,
                             @Param("role") Role role,
                             @Param("subscription") String subscription);

}