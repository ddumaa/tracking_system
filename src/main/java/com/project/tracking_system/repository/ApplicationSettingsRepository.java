package com.project.tracking_system.repository;

import com.project.tracking_system.entity.ApplicationSettings;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Репозиторий настроек приложения.
 */
public interface ApplicationSettingsRepository extends JpaRepository<ApplicationSettings, Long> {
}
