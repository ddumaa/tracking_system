package com.project.tracking_system.controller;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Перечисление доступных команд управления заявками на возврат.
 */
enum ReturnRequestCommandType {

    /** Перевести заявку в режим возврата. */
    SET_MODE_RETURN("SET_MODE_RETURN"),

    /** Перевести заявку в режим обмена. */
    SET_MODE_EXCHANGE("SET_MODE_EXCHANGE"),

    /** Зарегистрировать вручную обменную посылку. */
    REGISTER_EXCHANGE_PARCEL("REGISTER_EXCHANGE_PARCEL"),

    /** Отметить отправку обменной посылки. */
    MARK_EXCHANGE_SENT("MARK_EXCHANGE_SENT"),

    /** Отметить доставку обменной посылки покупателю. */
    MARK_EXCHANGE_DELIVERED("MARK_EXCHANGE_DELIVERED"),

    /** Обновить обратный трек или комментарий заявки. */
    UPDATE_REVERSE_TRACK("UPDATE_REVERSE_TRACK"),

    /** Отметить отправку возврата покупателем. */
    MARK_OUTBOUND_SENT("MARK_OUTBOUND_SENT"),

    /** Отметить прибытие возврата в пункт назначения. */
    MARK_INBOUND_ARRIVED("MARK_INBOUND_ARRIVED"),

    /** Отметить приём возврата магазином. */
    MARK_INBOUND_PICKED_UP("MARK_INBOUND_PICKED_UP"),

    /** Закрыть заявку. */
    CLOSE_REQUEST("CLOSE_REQUEST");

    private static final Map<String, ReturnRequestCommandType> LEGACY_CODES;

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
                .orElseGet(() -> {
                    ReturnRequestCommandType legacy = LEGACY_CODES.get(code);
                    if (legacy != null) {
                        return legacy;
                    }
                    return LEGACY_CODES.get(code != null ? code.toUpperCase() : null);
                });
    }

    static {
        Map<String, ReturnRequestCommandType> legacy = new HashMap<>();
        legacy.put("START_EXCHANGE", SET_MODE_EXCHANGE);
        legacy.put("CREATE_EXCHANGE_PARCEL", REGISTER_EXCHANGE_PARCEL);
        legacy.put("CANCEL_EXCHANGE", CLOSE_REQUEST);
        legacy.put("REOPEN_RETURN", SET_MODE_RETURN);
        legacy.put("CONFIRM_RECEIPT", MARK_INBOUND_PICKED_UP);
        legacy.put("UPDATE_DETAILS", UPDATE_REVERSE_TRACK);
        legacy.put("CLOSE", CLOSE_REQUEST);
        legacy.put("MARK_EXCHANGE_REGISTERED", SET_MODE_EXCHANGE);
        LEGACY_CODES = Collections.unmodifiableMap(legacy);
    }
}
