package com.project.tracking_system.repository;

import com.project.tracking_system.entity.Role;
import com.project.tracking_system.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository <User, Long> {

    Optional<User> findByEmail(String username);

    Optional<User> findById(Long userId);

    @Query("""
        SELECT u FROM User u
        JOIN u.roles r
        WHERE r = 'ROLE_PAID_USER'
        """)
    List<User> findUsersByRole(@Param("role") Role role);

    List<User> findByRolesContaining(Role role);

    long countByRolesContaining(Role role);

    @Query("""
        SELECT COUNT(u) > 0 FROM User u
        JOIN u.roles r
        WHERE u.id = :userId
        AND r = 'ROLE_PAID_USER'
        """)
    boolean isUserPaid(@Param("userId") Long userId);

    @Query("SELECT u FROM User u JOIN u.roles r WHERE r = 'ROLE_PAID_USER' AND u.roleExpirationDate < :now")
    List<User> findExpiredPaidUsers(@Param("now") ZonedDateTime now);

    @Query("SELECT u.id FROM User u JOIN u.roles r WHERE r = 'ROLE_PAID_USER' AND (u.roleExpirationDate IS NULL OR u.roleExpirationDate >= :now)")
    List<Long> findActivePaidUsers(@Param("now") ZonedDateTime now);

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


}