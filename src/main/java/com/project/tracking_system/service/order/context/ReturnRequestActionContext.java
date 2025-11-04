package com.project.tracking_system.service.order.context;

import com.project.tracking_system.entity.OrderReturnRequest;
import com.project.tracking_system.entity.OrderReturnRequestStatus;
import com.project.tracking_system.entity.ReturnRequestAction;
import com.project.tracking_system.entity.ReturnRequestMode;
import com.project.tracking_system.entity.ReturnRequestStage;

import java.util.EnumMap;
import java.util.Objects;

/**
 * Контекст расчёта доступных действий по заявке на возврат/обмен.
 * <p>
 * Объект содержит предрасчитанные ограничения по каждому действию и основные атрибуты заявки,
 * чтобы {@code ReturnRequestWorkflow} мог сосредоточиться на матрице переходов
 * и не зависеть от сервисов, проверяющих бизнес-правила (SRP, DIP).
 * </p>
 */
public final class ReturnRequestActionContext {

    private final OrderReturnRequest request;
    private final ReturnRequestMode mode;
    private final ReturnRequestStage stage;
    private final OrderReturnRequestStatus status;
    private final EnumMap<ReturnRequestAction, Boolean> availability;

    private ReturnRequestActionContext(Builder builder) {
        this.request = builder.request;
        this.mode = builder.mode;
        this.stage = builder.stage;
        this.status = builder.status;
        this.availability = new EnumMap<>(builder.availability);
    }

    /**
     * Возвращает исходную заявку.
     */
    public OrderReturnRequest request() {
        return request;
    }

    /**
     * Возвращает режим обработки заявки.
     */
    public ReturnRequestMode mode() {
        return mode;
    }

    /**
     * Возвращает актуальную стадию заявки.
     */
    public ReturnRequestStage stage() {
        return stage;
    }

    /**
     * Возвращает статус заявки.
     */
    public OrderReturnRequestStatus status() {
        return status;
    }

    /**
     * Проверяет, разрешено ли конкретное действие согласно подготовленным правилам.
     *
     * @param action проверяемое действие
     * @return {@code true}, если действие доступно
     */
    public boolean allows(ReturnRequestAction action) {
        if (action == null) {
            return false;
        }
        return availability.getOrDefault(action, Boolean.FALSE);
    }

    /**
     * Создаёт билдер контекста для указанной заявки.
     */
    public static Builder builder(OrderReturnRequest request) {
        return new Builder(request);
    }

    /**
     * Билдер контекста, позволяющий поэтапно заполнить данные для workflow.
     */
    public static final class Builder {

        private final OrderReturnRequest request;
        private ReturnRequestMode mode;
        private ReturnRequestStage stage;
        private OrderReturnRequestStatus status;
        private final EnumMap<ReturnRequestAction, Boolean> availability = new EnumMap<>(ReturnRequestAction.class);

        private Builder(OrderReturnRequest request) {
            this.request = request;
        }

        /**
         * Устанавливает режим обработки заявки.
         */
        public Builder withMode(ReturnRequestMode mode) {
            this.mode = mode;
            return this;
        }

        /**
         * Устанавливает текущую стадию заявки.
         */
        public Builder withStage(ReturnRequestStage stage) {
            this.stage = stage;
            return this;
        }

        /**
         * Устанавливает статус заявки.
         */
        public Builder withStatus(OrderReturnRequestStatus status) {
            this.status = status;
            return this;
        }

        /**
         * Фиксирует доступность конкретного действия.
         *
         * @param action действие, чью доступность требуется описать
         * @param allowed {@code true}, если действие разрешено
         */
        public Builder allow(ReturnRequestAction action, boolean allowed) {
            if (action != null) {
                availability.put(action, allowed);
            }
            return this;
        }

        /**
         * Создаёт неизменяемый контекст после заполнения обязательных полей.
         */
        public ReturnRequestActionContext build() {
            Objects.requireNonNull(mode, "Не указан режим заявки для контекста действий");
            Objects.requireNonNull(stage, "Не указана стадия заявки для контекста действий");
            return new ReturnRequestActionContext(this);
        }
    }
}
