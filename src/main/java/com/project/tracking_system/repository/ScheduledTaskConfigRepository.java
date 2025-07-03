package com.project.tracking_system.repository;

import com.project.tracking_system.entity.ScheduledTaskConfig;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Репозиторий конфигураций планировщика задач.
 */
public interface ScheduledTaskConfigRepository extends JpaRepository<ScheduledTaskConfig, Long> {

}