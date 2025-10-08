package com.project.tracking_system.entity;

import jakarta.persistence.*;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;

/**
 * Заявка на возврат или обмен по конкретной посылке.
 * <p>
 * Сущность фиксирует идемпотентный ключ, автора и время создания,
 * а также результат рассмотрения: запуск обмена или закрытие без него.
 * </p>
 */
@Entity
@Table(name = "tb_order_return_requests",
        uniqueConstraints = @UniqueConstraint(name = "ux_order_return_requests_key", columnNames = "idempotency_key"))
public class OrderReturnRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Эпизод заказа, к которому относится заявка.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "episode_id", nullable = false)
    private OrderEpisode episode;

    /**
     * Посылка, ставшая основанием для заявки.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "parcel_id", nullable = false)
    private TrackParcel parcel;

    /**
     * Пользователь, зарегистрировавший заявку.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by", nullable = false, updatable = false)
    private User createdBy;

    /**
     * Время регистрации заявки в UTC.
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    private ZonedDateTime createdAt = ZonedDateTime.now(ZoneOffset.UTC);

    /**
     * Время, когда пользователь запросил возврат.
     */
    @Column(name = "requested_at", nullable = false)
    private ZonedDateTime requestedAt = ZonedDateTime.now(ZoneOffset.UTC);

    /**
     * Причина, по которой оформляется возврат.
     */
    @Column(name = "reason", nullable = false, length = 255)
    private String reason;

    /**
     * Дополнительный комментарий пользователя.
     */
    @Column(name = "comment", columnDefinition = "TEXT")
    private String comment;

    /**
     * Трек-номер обратной отправки, если он известен.
     */
    @Column(name = "reverse_track_number", length = 64)
    private String reverseTrackNumber;

    /**
     * Признак, что магазин подтвердил получение возврата вручную.
     */
    @Column(name = "return_receipt_confirmed", nullable = false)
    private boolean returnReceiptConfirmed = false;

    /**
     * Время, когда менеджер подтвердил получение возврата.
     */
    @Column(name = "return_receipt_confirmed_at")
    private ZonedDateTime returnReceiptConfirmedAt;

    /**
     * Признак, что пользователь запросил обмен при регистрации заявки.
     */
    @Column(name = "exchange_requested", nullable = false)
    private boolean exchangeRequested = false;

    /**
     * Текущее состояние заявки.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private OrderReturnRequestStatus status = OrderReturnRequestStatus.REGISTERED;

    /**
     * Пользователь, одобривший запуск обмена.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "decision_by")
    private User decisionBy;

    /**
     * Время принятия решения об обмене.
     */
    @Column(name = "decision_at")
    private ZonedDateTime decisionAt;

    /**
     * Пользователь, закрывший заявку без обмена.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "closed_by")
    private User closedBy;

    /**
     * Время закрытия заявки.
     */
    @Column(name = "closed_at")
    private ZonedDateTime closedAt;

    /**
     * Идемпотентный ключ, предотвращающий повторное создание одинаковых заявок.
     */
    @Column(name = "idempotency_key", nullable = false, updatable = false)
    private String idempotencyKey;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    public Long getId() {
        return id;
    }

    public OrderEpisode getEpisode() {
        return episode;
    }

    public void setEpisode(OrderEpisode episode) {
        this.episode = episode;
    }

    public TrackParcel getParcel() {
        return parcel;
    }

    public void setParcel(TrackParcel parcel) {
        this.parcel = parcel;
    }

    public User getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(User createdBy) {
        this.createdBy = createdBy;
    }

    public ZonedDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(ZonedDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public ZonedDateTime getRequestedAt() {
        return requestedAt;
    }

    public void setRequestedAt(ZonedDateTime requestedAt) {
        this.requestedAt = requestedAt;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    public String getReverseTrackNumber() {
        return reverseTrackNumber;
    }

    public void setReverseTrackNumber(String reverseTrackNumber) {
        this.reverseTrackNumber = reverseTrackNumber;
    }

    public boolean isReturnReceiptConfirmed() {
        return returnReceiptConfirmed;
    }

    public void setReturnReceiptConfirmed(boolean returnReceiptConfirmed) {
        this.returnReceiptConfirmed = returnReceiptConfirmed;
    }

    public ZonedDateTime getReturnReceiptConfirmedAt() {
        return returnReceiptConfirmedAt;
    }

    public void setReturnReceiptConfirmedAt(ZonedDateTime returnReceiptConfirmedAt) {
        this.returnReceiptConfirmedAt = returnReceiptConfirmedAt;
    }

    public boolean isExchangeRequested() {
        return exchangeRequested;
    }

    public void setExchangeRequested(boolean exchangeRequested) {
        this.exchangeRequested = exchangeRequested;
    }

    public OrderReturnRequestStatus getStatus() {
        return status;
    }

    public void setStatus(OrderReturnRequestStatus status) {
        this.status = status;
    }

    public User getDecisionBy() {
        return decisionBy;
    }

    public void setDecisionBy(User decisionBy) {
        this.decisionBy = decisionBy;
    }

    public ZonedDateTime getDecisionAt() {
        return decisionAt;
    }

    public void setDecisionAt(ZonedDateTime decisionAt) {
        this.decisionAt = decisionAt;
    }

    public User getClosedBy() {
        return closedBy;
    }

    public void setClosedBy(User closedBy) {
        this.closedBy = closedBy;
    }

    public ZonedDateTime getClosedAt() {
        return closedAt;
    }

    public void setClosedAt(ZonedDateTime closedAt) {
        this.closedAt = closedAt;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }

    public long getVersion() {
        return version;
    }

    public void setVersion(long version) {
        this.version = version;
    }

    /**
     * Проверяет, ожидает ли заявка решения.
     */
    public boolean requiresAction() {
        return status == OrderReturnRequestStatus.REGISTERED;
    }

    /**
     * Проверяет, одобрен ли обмен.
     */
    public boolean isExchangeApproved() {
        return status == OrderReturnRequestStatus.EXCHANGE_APPROVED;
    }
}

