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

    /** Отменить запущенный обмен. */
    CANCEL_EXCHANGE("CANCEL_EXCHANGE"),

    /** Перевести обмен обратно в возврат. */
    REOPEN_RETURN("REOPEN_RETURN"),

    /** Подтвердить приём возврата магазином. */
    CONFIRM_RECEIPT("CONFIRM_RECEIPT"),

    /** Обновить данные заявки (треки и комментарии). */
    UPDATE_DETAILS("UPDATE_DETAILS"),

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
