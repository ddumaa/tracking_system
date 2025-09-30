package com.project.tracking_system.repository;

import com.project.tracking_system.entity.TrackNumberAudit;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Репозиторий для сохранения записей аудита изменения трек-номеров.
 */
public interface TrackNumberAuditRepository extends JpaRepository<TrackNumberAudit, Long> {
}
