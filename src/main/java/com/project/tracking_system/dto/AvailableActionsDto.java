package com.project.tracking_system.dto;

import com.project.tracking_system.entity.ReturnRequestAction;

import java.util.EnumSet;
import java.util.List;

/**
 * Обёртка над списком кодов доступных действий с заявкой на возврат/обмен.
 */
public final class AvailableActionsDto {

    private final List<String> actions;
    private final EnumSet<ReturnRequestAction> actionSet;

    /**
     * Создаёт DTO доступных действий, гарантируя неизменяемость списка.
     *
     * @param actions коды доступных действий; {@code null} заменяется на пустой список
     */
    public AvailableActionsDto(List<String> actions) {
        this.actions = actions == null ? List.of() : List.copyOf(actions);
        this.actionSet = EnumSet.noneOf(ReturnRequestAction.class);
        this.actions.stream()
                .map(ReturnRequestAction::fromCode)
                .forEach(action -> {
                    if (action != null) {
                        actionSet.add(action);
                    }
                });
    }

    /**
     * Возвращает список кодов доступных действий.
     *
     * @return неизменяемый список кодов действий
     */
    public List<String> getActions() {
        return actions;
    }

    /** Проверяет, можно ли перевести заявку в обмен. */
    public boolean isSetModeExchange() {
        return contains(ReturnRequestAction.SET_MODE_EXCHANGE);
    }

    /** Проверяет, можно ли вернуть заявку в режим возврата. */
    public boolean isSetModeReturn() {
        return contains(ReturnRequestAction.SET_MODE_RETURN);
    }

    /** Проверяет, можно ли зарегистрировать обменную посылку. */
    public boolean isRegisterExchangeParcel() {
        return contains(ReturnRequestAction.REGISTER_EXCHANGE_PARCEL);
    }

    /** Проверяет, доступно ли обновление обратного трека. */
    public boolean isUpdateReverseTrack() {
        return contains(ReturnRequestAction.UPDATE_REVERSE_TRACK);
    }

    /** Проверяет, можно ли закрыть заявку. */
    public boolean isCloseRequest() {
        return contains(ReturnRequestAction.CLOSE_REQUEST);
    }

    /** Проверяет, можно ли отметить отправку возврата. */
    public boolean isMarkOutboundSent() {
        return contains(ReturnRequestAction.MARK_OUTBOUND_SENT);
    }

    /** Проверяет, можно ли отметить прибытие возврата. */
    public boolean isMarkInboundArrived() {
        return contains(ReturnRequestAction.MARK_INBOUND_ARRIVED);
    }

    /** Проверяет, можно ли отметить приём возврата. */
    public boolean isMarkInboundPickedUp() {
        return contains(ReturnRequestAction.MARK_INBOUND_PICKED_UP);
    }

    /** Проверяет, можно ли отметить отправку обменной посылки. */
    public boolean isMarkExchangeSent() {
        return contains(ReturnRequestAction.MARK_EXCHANGE_SENT);
    }

    /** Проверяет, можно ли отметить доставку обменной посылки. */
    public boolean isMarkExchangeDelivered() {
        return contains(ReturnRequestAction.MARK_EXCHANGE_DELIVERED);
    }

    private boolean contains(ReturnRequestAction action) {
        return actionSet.contains(action);
    }
}
