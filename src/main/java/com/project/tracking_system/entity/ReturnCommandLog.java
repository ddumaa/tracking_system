package com.project.tracking_system.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;

/**
 * Журнал выполнения команд по заявкам возвратов.
 * <p>
 * Сущность хранит ключ идемпотентности, тип действия и хеш полезной нагрузки,
 * что позволяет выявлять повторные и конфликтующие команды. Снимок ответа
 * сохраняется в виде JSON, чтобы повторные вызовы возвращали идентичный
 * результат независимо от последующих изменений заявки.
 * </p>
 */
@Entity
@Table(name = "tb_return_command_logs",
        uniqueConstraints = @UniqueConstraint(name = "ux_return_command_log_key",
                columnNames = {"request_id", "idempotency_key"}))
public class ReturnCommandLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Идентификатор заявки, к которой относится команда.
     */
    @Column(name = "request_id", nullable = false)
    private Long requestId;

    /**
     * Ключ идемпотентности, предоставленный клиентом.
     */
    @Column(name = "idempotency_key", nullable = false, length = 255)
    private String idempotencyKey;

    /**
     * Код выполненной команды.
     */
    @Column(name = "action", nullable = false, length = 100)
    private String action;

    /**
     * Хеш полезной нагрузки, используемый для обнаружения конфликтов.
     */
    @Column(name = "payload_hash", nullable = false, length = 64)
    private String payloadHash;

    /**
     * Снимок ответа клиенту после выполнения команды.
     */
    @Lob
    @Column(name = "response_snapshot", nullable = false, columnDefinition = "TEXT")
    private String responseSnapshot;

    /**
     * Момент записи выполнения команды.
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    private ZonedDateTime createdAt = ZonedDateTime.now(ZoneOffset.UTC);

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getRequestId() {
        return requestId;
    }

    public void setRequestId(Long requestId) {
        this.requestId = requestId;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getPayloadHash() {
        return payloadHash;
    }

    public void setPayloadHash(String payloadHash) {
        this.payloadHash = payloadHash;
    }

    public String getResponseSnapshot() {
        return responseSnapshot;
    }

    public void setResponseSnapshot(String responseSnapshot) {
        this.responseSnapshot = responseSnapshot;
    }

    public ZonedDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(ZonedDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
