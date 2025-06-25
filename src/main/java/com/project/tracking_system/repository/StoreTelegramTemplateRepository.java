package com.project.tracking_system.repository;

import com.project.tracking_system.entity.StoreTelegramSettings;
import com.project.tracking_system.entity.StoreTelegramTemplate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Репозиторий шаблонов Telegram-сообщений магазина.
 */
public interface StoreTelegramTemplateRepository extends JpaRepository<StoreTelegramTemplate, Long> {

    /**
     * Найти все шаблоны для настроек.
     *
     * @param settingsId идентификатор настроек
     * @return список шаблонов
     */
    List<StoreTelegramTemplate> findBySettingsId(Long settingsId);

}