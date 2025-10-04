package com.project.tracking_system.entity;

import jakarta.persistence.*;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;

/**
 * Запрос покупателя магазину по активной заявке на обмен.
 * <p>
 * Используется, когда отмена или перевод обмена невозможны автоматически
 * и требуется подтверждение со стороны магазина.
 * </p>
 */
@Entity
@Table(name = "tb_return_request_action_requests")
public class OrderReturnRequestActionRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Заявка, по которой требуется действие магазина.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "return_request_id", nullable = false)
    private OrderReturnRequest returnRequest;

    /**
     * Покупатель, инициировавший запрос.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    /**
     * Тип действия, которое необходимо выполнить магазину.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false, length = 32)
    private OrderReturnRequestActionType action;

    /**
     * Время формирования запроса.
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    private ZonedDateTime createdAt = ZonedDateTime.now(ZoneOffset.UTC);

    /**
     * Признак обработки запроса магазином.
     */
    @Column(name = "processed_at")
    private ZonedDateTime processedAt;

    public Long getId() {
        return id;
    }

    public OrderReturnRequest getReturnRequest() {
        return returnRequest;
    }

    public void setReturnRequest(OrderReturnRequest returnRequest) {
        this.returnRequest = returnRequest;
    }

    public Customer getCustomer() {
        return customer;
    }

    public void setCustomer(Customer customer) {
        this.customer = customer;
    }

    public OrderReturnRequestActionType getAction() {
        return action;
    }

    public void setAction(OrderReturnRequestActionType action) {
        this.action = action;
    }

    public ZonedDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(ZonedDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public ZonedDateTime getProcessedAt() {
        return processedAt;
    }

    public void setProcessedAt(ZonedDateTime processedAt) {
        this.processedAt = processedAt;
    }

    /**
     * Возвращает, обработан ли запрос магазином.
     */
    public boolean isProcessed() {
        return processedAt != null;
    }
}
