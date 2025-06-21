package com.project.tracking_system.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import java.util.Objects;

/**
 * Конфигурация планируемой задачи.
 */
@Getter
@Setter
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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ScheduledTaskConfig that = (ScheduledTaskConfig) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
