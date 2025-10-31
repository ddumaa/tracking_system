package com.project.tracking_system.entity;

import jakarta.persistence.*;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Objects;

/**
 * История изменений заявки на возврат/обмен.
 * <p>
 * Каждая запись фиксирует этап, режим и актуальные трек-номера в момент изменения.
 * Записи используются для построения таймлайна обработки и аудита действий менеджеров.
 * </p>
 */
@Entity
@Table(name = "tb_order_return_request_history")
public class OrderReturnRequestHistoryEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Заявка, к которой относится запись истории.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "return_request_id", nullable = false)
    private OrderReturnRequest returnRequest;

    /**
     * Режим обработки заявки на момент изменения.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "mode", nullable = false)
    private ReturnRequestMode mode = ReturnRequestMode.RETURN;

    /**
     * Этап обработки заявки.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "stage", nullable = false)
    private ReturnRequestStage stage = ReturnRequestStage.NEW;

    /**
     * Трек обратной отправки на момент фиксации события.
     */
    @Column(name = "reverse_track_number", length = 64)
    private String reverseTrackNumber;

    /**
     * Трек обменной посылки на момент фиксации события.
     */
    @Column(name = "exchange_track_number", length = 64)
    private String exchangeTrackNumber;

    /**
     * Признак, что изменение выполнено вручную менеджером.
     */
    @Column(name = "manual_transition", nullable = false)
    private boolean manualTransition = false;

    /**
     * Пользователь, выполнивший изменение.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "changed_by")
    private User changedBy;

    /**
     * Время фиксации изменения.
     */
    @Column(name = "changed_at", nullable = false, updatable = false)
    private ZonedDateTime changedAt = ZonedDateTime.now(ZoneOffset.UTC);

    public Long getId() {
        return id;
    }

    public OrderReturnRequest getReturnRequest() {
        return returnRequest;
    }

    public void setReturnRequest(OrderReturnRequest returnRequest) {
        this.returnRequest = returnRequest;
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

    public String getReverseTrackNumber() {
        return reverseTrackNumber;
    }

    public void setReverseTrackNumber(String reverseTrackNumber) {
        this.reverseTrackNumber = reverseTrackNumber;
    }

    public String getExchangeTrackNumber() {
        return exchangeTrackNumber;
    }

    public void setExchangeTrackNumber(String exchangeTrackNumber) {
        this.exchangeTrackNumber = exchangeTrackNumber;
    }

    public boolean isManualTransition() {
        return manualTransition;
    }

    public void setManualTransition(boolean manualTransition) {
        this.manualTransition = manualTransition;
    }

    public User getChangedBy() {
        return changedBy;
    }

    public void setChangedBy(User changedBy) {
        this.changedBy = changedBy;
    }

    public ZonedDateTime getChangedAt() {
        return changedAt;
    }

    public void setChangedAt(ZonedDateTime changedAt) {
        this.changedAt = changedAt;
    }

    /**
     * Создаёт запись истории на основе текущего состояния заявки.
     *
     * @param request          заявка, состояние которой фиксируется
     * @param manualTransition признак, что изменение инициировано вручную
     * @param actor            пользователь, выполнивший действие
     * @param moment           момент фиксации события
     * @return заполненная запись истории
     */
    public static OrderReturnRequestHistoryEntry snapshotOf(OrderReturnRequest request,
                                                            boolean manualTransition,
                                                            User actor,
                                                            ZonedDateTime moment) {
        OrderReturnRequestHistoryEntry entry = new OrderReturnRequestHistoryEntry();
        entry.setReturnRequest(request);
        entry.setMode(request != null ? request.getMode() : ReturnRequestMode.RETURN);
        entry.setStage(request != null ? request.getStage() : ReturnRequestStage.NEW);
        entry.setReverseTrackNumber(request != null ? request.getReverseTrackNumber() : null);
        entry.setExchangeTrackNumber(request != null ? request.getExchangeTrackNumber() : null);
        entry.setManualTransition(manualTransition);
        entry.setChangedBy(actor);
        entry.setChangedAt(moment != null ? moment : ZonedDateTime.now(ZoneOffset.UTC));
        return entry;
    }
}
