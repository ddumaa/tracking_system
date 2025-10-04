package com.project.tracking_system.repository;

import com.project.tracking_system.entity.OrderReturnRequestActionRequest;
import com.project.tracking_system.entity.OrderReturnRequestActionType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Репозиторий запросов покупателей к магазину по активным обменам.
 */
public interface OrderReturnRequestActionRequestRepository extends JpaRepository<OrderReturnRequestActionRequest, Long> {

    Optional<OrderReturnRequestActionRequest> findFirstByReturnRequest_IdAndActionAndProcessedAtIsNull(Long requestId,
                                                                                                     OrderReturnRequestActionType action);
}
