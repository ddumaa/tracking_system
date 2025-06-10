package com.project.tracking_system.repository;

import com.project.tracking_system.entity.EvropostServiceCredential;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/**
 * @author Dmitriy Anisimov
 * @date 09.03.2025
 */
public interface EvropostServiceCredentialRepository extends JpaRepository<EvropostServiceCredential, Long> {

    /**
     * Проверить, использует ли пользователь собственные учётные данные для API Европочты.
     *
     * @param userId идентификатор пользователя
     * @return {@code true}, если пользователь использует свои данные
     */
    @Query("SELECT esc.useCustomCredentials FROM EvropostServiceCredential esc WHERE esc.user.id = :userId")
    boolean isUsingCustomCredentials(Long userId);

}