package com.project.tracking_system.repository;

import com.project.tracking_system.entity.Role;
import com.project.tracking_system.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository <User, Long> {

    Optional<User> findByEmail(String username);

    @Query("""
        SELECT u FROM User u
        JOIN u.roles r
        WHERE r = 'ROLE_PAID_USER'
    """)
    List<User> findUsersByRole(@Param("role") Role role);

    List<User> findByRolesContaining(Role role);

    long countByRolesContaining(Role role);

}