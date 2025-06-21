package com.project.tracking_system.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Конфигурация планируемой задачи.
 */
@Data
@NoArgsConstructor
@Entity
@Table(name = "tb_scheduled_task_config")
public class ScheduledTaskConfig {

    @Id
    private Long id;

    private String description;

    @Column(nullable = false)
    private String cron;

    private String zone;

    /**
     * Устанавливает таймзону по умолчанию, если она не указана.
     */
    @PrePersist
    @PreUpdate
    private void ensureZone() {
        if (zone == null || zone.isBlank()) {
            zone = "UTC";
        }
    }
}
