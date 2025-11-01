package com.project.tracking_system.controller;

import java.util.Arrays;

/**
 * Перечисление доступных команд управления заявками на возврат.
 */
enum ReturnRequestCommandType {

    /** Перевести заявку из обмена обратно в возврат. */
    SET_MODE_RETURN("set_mode_return"),

    /** Перевести заявку в режим обмена. */
    SET_MODE_EXCHANGE("set_mode_exchange"),

    /** Отметить исходящую отправку покупателя. */
    MARK_OUTBOUND_SENT("mark_outbound_sent"),

    /** Зафиксировать прибытие возврата в магазин. */
    MARK_INBOUND_ARRIVED("mark_inbound_arrived"),

    /** Подтвердить выдачу возврата сотруднику склада. */
    MARK_INBOUND_PICKED_UP("mark_inbound_picked_up"),

    /** Зарегистрировать обменную посылку магазина. */
    REGISTER_EXCHANGE_PARCEL("register_exchange_parcel"),

    /** Отметить отправку обменной посылки покупателю. */
    MARK_EXCHANGE_SENT("mark_exchange_sent"),

    /** Подтвердить доставку обменной посылки. */
    MARK_EXCHANGE_DELIVERED("mark_exchange_delivered"),

    /** Обновить данные обратного трека и комментарий. */
    UPDATE_REVERSE_TRACK("update_reverse_track"),

    /** Закрыть обращение после завершения обработки. */
    CLOSE_REQUEST("close_request");

    private final String code;

    ReturnRequestCommandType(String code) {
        this.code = code;
    }

    /**
     * Восстанавливает тип команды по строковому коду без учёта регистра.
     *
     * @param code код команды из запроса
     * @return найденный тип или {@code null}, если код неизвестен
     */
    public static ReturnRequestCommandType fromCode(String code) {
        if (code == null) {
            return null;
        }
        return Arrays.stream(values())
                .filter(item -> item.code.equalsIgnoreCase(code))
                .findFirst()
                .orElse(null);
    }
}
