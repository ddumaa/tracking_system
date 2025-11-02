package com.project.tracking_system.controller;

import java.util.Arrays;

/**
 * Перечисление доступных команд управления заявками на возврат.
 */
enum ReturnRequestCommandType {

    /** Запустить обмен по заявке возврата. */
    START_EXCHANGE("START_EXCHANGE"),

    /** Создать обменную посылку для одобренного обмена. */
    CREATE_EXCHANGE_PARCEL("CREATE_EXCHANGE_PARCEL"),

    /** Зарегистрировать вручную обменную посылку. */
    REGISTER_EXCHANGE_PARCEL("REGISTER_EXCHANGE_PARCEL"),

    /** Отметить отправку обменной посылки. */
    MARK_EXCHANGE_SENT("MARK_EXCHANGE_SENT"),

    /** Отметить доставку обменной посылки покупателю. */
    MARK_EXCHANGE_DELIVERED("MARK_EXCHANGE_DELIVERED"),

    /** Отменить запущенный обмен. */
    CANCEL_EXCHANGE("CANCEL_EXCHANGE"),

    /** Перевести обмен обратно в возврат. */
    REOPEN_RETURN("REOPEN_RETURN"),

    /** Подтвердить приём возврата магазином. */
    CONFIRM_RECEIPT("CONFIRM_RECEIPT"),

    /** Обновить данные заявки (треки и комментарии). */
    UPDATE_DETAILS("UPDATE_DETAILS"),

    /** Отметить отправку возврата покупателем. */
    MARK_OUTBOUND_SENT("MARK_OUTBOUND_SENT"),

    /** Отметить прибытие возврата в пункт назначения. */
    MARK_INBOUND_ARRIVED("MARK_INBOUND_ARRIVED"),

    /** Отметить приём возврата магазином. */
    MARK_INBOUND_PICKED_UP("MARK_INBOUND_PICKED_UP"),

    /** Закрыть заявку без запуска обмена. */
    CLOSE("CLOSE");

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
