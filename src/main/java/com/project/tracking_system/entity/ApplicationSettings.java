package com.project.tracking_system.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Глобальные настройки приложения.
 * <p>Содержит параметры, влияющие на работу всего сервиса.</p>
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "tb_application_settings")
public class ApplicationSettings {

    /**
     * Единственный идентификатор записи. Используется значение {@code 1}.
     */
    @Id
    private Long id;

    /**
     * Интервал между автоматическими обновлениями треков в часах.
     */
    @Column(name = "track_update_interval_hours", nullable = false)
    private int trackUpdateIntervalHours;

    /**
     * Время хранения результатов обработки в кэше в миллисекундах.
     */
    @Column(name = "result_cache_expiration_ms", nullable = false)
    private long resultCacheExpirationMs;
}
