package com.project.tracking_system.repository;

import com.project.tracking_system.entity.UserSettings;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Репозиторий настроек пользователя.
 */
public interface UserSettingsRepository extends JpaRepository<UserSettings, Long> {

    /**
     * Найти настройки по идентификатору пользователя.
     *
     * @param userId идентификатор пользователя
     * @return настройки или {@code null}, если не найдены
     */
    @Query("SELECT s FROM UserSettings s WHERE s.user.id = :userId")
    UserSettings findByUserId(@Param("userId") Long userId);
}
