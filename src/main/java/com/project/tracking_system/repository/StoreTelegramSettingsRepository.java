package com.project.tracking_system.repository;

import com.project.tracking_system.entity.StoreTelegramSettings;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Репозиторий настроек Telegram для магазина.
 */
public interface StoreTelegramSettingsRepository extends JpaRepository<StoreTelegramSettings, Long> {

    /**
     * Найти настройки по идентификатору магазина.
     *
     * @param storeId идентификатор магазина
     * @return настройки или {@code null}, если не найдены
     */
    @Query("SELECT ts FROM StoreTelegramSettings ts LEFT JOIN FETCH ts.templates WHERE ts.store.id = :storeId")
    StoreTelegramSettings findByStoreId(@Param("storeId") Long storeId);

    /**
     * Подсчитать количество магазинов с включёнными напоминаниями.
     *
     * @return число магазинов
     */
    long countByRemindersEnabledTrue();
}
