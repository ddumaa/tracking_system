package com.project.tracking_system.repository;

import com.project.tracking_system.entity.ReturnCommandLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Репозиторий журнала выполнения команд возвратов.
 */
public interface ReturnCommandLogRepository extends JpaRepository<ReturnCommandLog, Long> {

    /**
     * Находит запись журнала по идентификатору заявки и ключу идемпотентности.
     *
     * @param requestId       идентификатор заявки
     * @param idempotencyKey  ключ идемпотентности
     * @return найденная запись или {@link Optional#empty()}, если команда не выполнялась
     */
    Optional<ReturnCommandLog> findFirstByRequestIdAndIdempotencyKey(Long requestId, String idempotencyKey);
}
