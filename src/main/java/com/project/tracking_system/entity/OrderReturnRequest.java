package com.project.tracking_system.entity;

import jakarta.persistence.*;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Заявка на возврат или обмен по конкретной посылке.
 * <p>
 * Сущность фиксирует идемпотентный ключ, автора и время создания,
 * а также результат рассмотрения: запуск обмена или закрытие без него.
 * Дополнительно хранится режим обработки, текущий этап и история изменений,
 * что позволяет отображать полный таймлайн действий магазина и покупателя.
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
     * Магазин, оформивший исходную посылку.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "store_id", nullable = false)
    private Store store;

    /**
     * Пользователь, зарегистрировавший заявку.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by", nullable = false, updatable = false)
    private User createdBy;

    /**
     * Пользователь магазина, ответственный за текущий этап обработки.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "responsible_id")
    private User responsibleManager;

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
     * Трек обменной посылки, если он присвоен вручную или при создании обмена.
     */
    @Column(name = "exchange_track_number", length = 64)
    private String exchangeTrackNumber;

    /**
     * Признак, что трек обратной отправки был задан вручную менеджером.
     */
    @Column(name = "manual_track_override", nullable = false)
    private boolean manualTrackOverride = false;

    /**
     * Признак, что менеджер вручную перевёл заявку на нужный этап.
     */
    @Column(name = "manual_stage_override", nullable = false)
    private boolean manualStageOverride = false;

    /**
     * Время, когда трек обменной посылки был установлен.
     */
    @Column(name = "exchange_track_assigned_at")
    private ZonedDateTime exchangeTrackAssignedAt;

    /**
     * Текущее состояние заявки.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private OrderReturnRequestStatus status = OrderReturnRequestStatus.REGISTERED;

    /**
     * Режим обработки заявки (возврат или обмен).
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "mode", nullable = false)
    private ReturnRequestMode mode = ReturnRequestMode.RETURN;

    /**
     * Текущий этап жизненного цикла заявки.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "stage", nullable = false)
    private ReturnRequestStage stage = ReturnRequestStage.NEW;

    /**
     * Время начала текущего этапа обработки.
     */
    @Column(name = "stage_started_at", nullable = false)
    private ZonedDateTime stageStartedAt = ZonedDateTime.now(ZoneOffset.UTC);

    /**
     * Время последнего обновления текущего этапа.
     */
    @Column(name = "stage_updated_at")
    private ZonedDateTime stageUpdatedAt;

    /**
     * Признак, что магазин подтвердил получение возврата вручную.
     * <p>
     * Флаг устанавливается сотрудником после проверки склада и исключает повторное подтверждение,
     * помогая сервисам корректно ограничивать действия пользователей.
     * </p>
     */
    @Column(name = "return_receipt_confirmed", nullable = false)
    private boolean returnReceiptConfirmed = false;

    /**
     * Время, когда магазин зафиксировал подтверждение получения возврата.
     * <p>
     * Метка позволяет отображать точный момент проверки в интерфейсе и в уведомлениях клиентов.
     * </p>
     */
    @Column(name = "return_receipt_confirmed_at")
    private ZonedDateTime returnReceiptConfirmedAt;

    /**
     * Признак, что пользователь запросил обмен при регистрации заявки.
     */
    @Column(name = "exchange_requested", nullable = false)
    private boolean exchangeRequested = false;

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

    /**
     * История изменений заявки по этапам и трекам.
     */
    @OneToMany(mappedBy = "returnRequest", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("changedAt ASC")
    private List<OrderReturnRequestHistoryEntry> historyEntries = new ArrayList<>();

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

    /**
     * Возвращает магазин, по которому оформлена заявка.
     */
    public Store getStore() {
        return store;
    }

    public void setStore(Store store) {
        this.store = store;
    }

    public User getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(User createdBy) {
        this.createdBy = createdBy;
    }

    /**
     * Возвращает менеджера, ответственного за текущий этап обработки.
     */
    public User getResponsibleManager() {
        return responsibleManager;
    }

    public void setResponsibleManager(User responsibleManager) {
        this.responsibleManager = responsibleManager;
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

    /**
     * Возвращает трек обменной посылки, если он уже известен.
     */
    public String getExchangeTrackNumber() {
        return exchangeTrackNumber;
    }

    public void setExchangeTrackNumber(String exchangeTrackNumber) {
        this.exchangeTrackNumber = exchangeTrackNumber;
    }

    public boolean isManualTrackOverride() {
        return manualTrackOverride;
    }

    public void setManualTrackOverride(boolean manualTrackOverride) {
        this.manualTrackOverride = manualTrackOverride;
    }

    public boolean isManualStageOverride() {
        return manualStageOverride;
    }

    public void setManualStageOverride(boolean manualStageOverride) {
        this.manualStageOverride = manualStageOverride;
    }

    public ZonedDateTime getExchangeTrackAssignedAt() {
        return exchangeTrackAssignedAt;
    }

    public void setExchangeTrackAssignedAt(ZonedDateTime exchangeTrackAssignedAt) {
        this.exchangeTrackAssignedAt = exchangeTrackAssignedAt;
    }

    public OrderReturnRequestStatus getStatus() {
        return status;
    }

    public void setStatus(OrderReturnRequestStatus status) {
        this.status = status;
    }

    public ReturnRequestMode getMode() {
        return mode;
    }

    public void setMode(ReturnRequestMode mode) {
        this.mode = Objects.requireNonNullElse(mode, ReturnRequestMode.RETURN);
    }

    public ReturnRequestStage getStage() {
        return stage;
    }

    public void setStage(ReturnRequestStage stage) {
        this.stage = Objects.requireNonNullElse(stage, ReturnRequestStage.NEW);
    }

    public ZonedDateTime getStageStartedAt() {
        return stageStartedAt;
    }

    public void setStageStartedAt(ZonedDateTime stageStartedAt) {
        this.stageStartedAt = stageStartedAt;
    }

    public ZonedDateTime getStageUpdatedAt() {
        return stageUpdatedAt;
    }

    public void setStageUpdatedAt(ZonedDateTime stageUpdatedAt) {
        this.stageUpdatedAt = stageUpdatedAt;
    }

    /**
     * Возвращает признак ручного подтверждения возврата магазином.
     *
     * @return {@code true}, если склад уже подтвердил получение возврата
     */
    public boolean isReturnReceiptConfirmed() {
        return returnReceiptConfirmed;
    }

    /**
     * Устанавливает признак ручного подтверждения возврата магазином.
     *
     * @param returnReceiptConfirmed новое значение флага подтверждения
     */
    public void setReturnReceiptConfirmed(boolean returnReceiptConfirmed) {
        this.returnReceiptConfirmed = returnReceiptConfirmed;
    }

    /**
     * Возвращает время, когда магазин подтвердил получение возврата.
     *
     * @return метка времени подтверждения или {@code null}, если подтверждения не было
     */
    public ZonedDateTime getReturnReceiptConfirmedAt() {
        return returnReceiptConfirmedAt;
    }

    /**
     * Устанавливает момент подтверждения возврата магазином.
     *
     * @param returnReceiptConfirmedAt новая метка времени подтверждения
     */
    public void setReturnReceiptConfirmedAt(ZonedDateTime returnReceiptConfirmedAt) {
        this.returnReceiptConfirmedAt = returnReceiptConfirmedAt;
    }

    public boolean isExchangeRequested() {
        return exchangeRequested;
    }

    public void setExchangeRequested(boolean exchangeRequested) {
        this.exchangeRequested = exchangeRequested;
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
     * Возвращает неизменяемую историю изменений заявки.
     */
    public List<OrderReturnRequestHistoryEntry> getHistoryEntries() {
        return Collections.unmodifiableList(historyEntries);
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

    /**
     * Возвращает режим обработки заявки (возврат или обмен) на основе текущих флагов.
     */
    public ReturnRequestMode getDerivedMode() {
        if (mode != null) {
            return mode;
        }
        if (isExchangeApproved() || isExchangeRequested()) {
            return ReturnRequestMode.EXCHANGE;
        }
        return ReturnRequestMode.RETURN;
    }

    /**
     * Создаёт снимок текущего состояния заявки для истории изменений.
     *
     * @param manualTransition признак ручного действия менеджера
     * @param actor             пользователь, инициировавший изменение
     * @param moment            момент фиксации изменения
     */
    public void snapshotHistory(boolean manualTransition, User actor, ZonedDateTime moment) {
        OrderReturnRequestHistoryEntry entry = OrderReturnRequestHistoryEntry.snapshotOf(this, manualTransition, actor, moment);
        addHistoryEntry(entry);
    }

    private void addHistoryEntry(OrderReturnRequestHistoryEntry entry) {
        if (entry == null) {
            return;
        }
        entry.setReturnRequest(this);
        historyEntries.add(entry);
    }
}
