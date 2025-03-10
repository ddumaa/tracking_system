package com.project.tracking_system.repository;

import com.project.tracking_system.entity.EvropostServiceCredential;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/**
 * @author Dmitriy Anisimov
 * @date 09.03.2025
 */
public interface EvropostServiceCredentialRepository extends JpaRepository<EvropostServiceCredential, Long> {

    @Query("SELECT esc.useCustomCredentials FROM EvropostServiceCredential esc WHERE esc.user.id = :userId")
    boolean isUsingCustomCredentials(Long userId);

}