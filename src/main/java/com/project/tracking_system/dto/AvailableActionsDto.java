package com.project.tracking_system.dto;

import com.project.tracking_system.entity.ReturnRequestAction;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Обёртка над списком кодов доступных действий с заявкой на возврат/обмен.
 */
public final class AvailableActionsDto {

    private final List<String> actions;
    private final List<String> actionCodes;
    private final EnumSet<ReturnRequestAction> actionSet;
    private final EnumMap<ReturnRequestAction, String> unavailableReasons;

    /**
     * Создаёт DTO доступных действий, гарантируя неизменяемость списка.
     *
     * @param actions коды доступных действий; {@code null} заменяется на пустой список
     */
    public AvailableActionsDto(List<String> actions) {
        this(actions, null, Map.of());
    }

    /**
     * Создаёт DTO доступных действий с явным перечислением доступных кодов и причин недоступности.
     *
     * @param actions            коды доступных действий; {@code null} заменяется на пустой список
     * @param availableActions   множество доступных действий, если оно уже вычислено
     * @param unavailableReasons причины недоступности действий
     */
    public AvailableActionsDto(List<String> actions,
                               EnumSet<ReturnRequestAction> availableActions,
                               Map<ReturnRequestAction, String> unavailableReasons) {
        List<String> normalizedCodes = actions == null ? List.of() : List.copyOf(actions);
        this.actions = normalizedCodes;
        this.actionCodes = normalizedCodes;
        EnumSet<ReturnRequestAction> normalizedSet = EnumSet.noneOf(ReturnRequestAction.class);
        if (availableActions != null) {
            normalizedSet.addAll(availableActions);
        }
        normalizedCodes.stream()
                .map(ReturnRequestAction::fromCode)
                .filter(Objects::nonNull)
                .forEach(normalizedSet::add);
        this.actionSet = normalizedSet;
        this.unavailableReasons = new EnumMap<>(ReturnRequestAction.class);
        if (unavailableReasons != null) {
            unavailableReasons.forEach((action, reason) -> {
                if (action != null) {
                    this.unavailableReasons.put(action, reason);
                }
            });
        }
    }

    /**
     * Возвращает список кодов доступных действий.
     *
     * @return неизменяемый список кодов действий
     */
    public List<String> getActions() {
        return actions;
    }

    /**
     * Возвращает список action-code в отдельном поле для фронтенда.
     */
    public List<String> getActionCodes() {
        return actionCodes;
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

    /** Возвращает причину недоступности перевода в возврат. */
    public String getSetModeReturnUnavailableReason() {
        return getUnavailableReason(ReturnRequestAction.SET_MODE_RETURN);
    }

    /** Возвращает причину недоступности перевода в обмен. */
    public String getSetModeExchangeUnavailableReason() {
        return getUnavailableReason(ReturnRequestAction.SET_MODE_EXCHANGE);
    }

    /** Возвращает причину недоступности регистрации обменной посылки. */
    public String getRegisterExchangeParcelUnavailableReason() {
        return getUnavailableReason(ReturnRequestAction.REGISTER_EXCHANGE_PARCEL);
    }

    /** Возвращает причину недоступности отметки отправки возврата. */
    public String getMarkOutboundSentUnavailableReason() {
        return getUnavailableReason(ReturnRequestAction.MARK_OUTBOUND_SENT);
    }

    /** Возвращает причину недоступности отметки прибытия возврата. */
    public String getMarkInboundArrivedUnavailableReason() {
        return getUnavailableReason(ReturnRequestAction.MARK_INBOUND_ARRIVED);
    }

    /** Возвращает причину недоступности подтверждения приёма возврата. */
    public String getMarkInboundPickedUpUnavailableReason() {
        return getUnavailableReason(ReturnRequestAction.MARK_INBOUND_PICKED_UP);
    }

    /** Возвращает причину недоступности отметки отправки замены. */
    public String getMarkExchangeSentUnavailableReason() {
        return getUnavailableReason(ReturnRequestAction.MARK_EXCHANGE_SENT);
    }

    /** Возвращает причину недоступности отметки доставки замены. */
    public String getMarkExchangeDeliveredUnavailableReason() {
        return getUnavailableReason(ReturnRequestAction.MARK_EXCHANGE_DELIVERED);
    }

    /** Возвращает причину недоступности закрытия обращения. */
    public String getCloseRequestUnavailableReason() {
        return getUnavailableReason(ReturnRequestAction.CLOSE_REQUEST);
    }

    /** Возвращает причину недоступности обновления обратного трека. */
    public String getUpdateReverseTrackUnavailableReason() {
        return getUnavailableReason(ReturnRequestAction.UPDATE_REVERSE_TRACK);
    }

    private boolean contains(ReturnRequestAction action) {
        return actionSet.contains(action);
    }

    private String getUnavailableReason(ReturnRequestAction action) {
        return unavailableReasons.get(action);
    }
}
