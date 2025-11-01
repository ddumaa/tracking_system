package com.project.tracking_system.controller;

import java.util.Arrays;

/**
 * Перечисление доступных команд управления заявками на возврат.
 */
enum ReturnRequestCommandType {

    /** Запустить обмен по заявке возврата. */
    START_EXCHANGE("start_exchange"),

    /** Создать обменную посылку для одобренного обмена. */
    CREATE_EXCHANGE_PARCEL("create_exchange_parcel"),

    /** Отменить запущенный обмен. */
    CANCEL_EXCHANGE("cancel_exchange"),

    /** Перевести обмен обратно в возврат. */
    REOPEN_RETURN("reopen_return"),

    /** Подтвердить приём возврата магазином. */
    CONFIRM_RECEIPT("confirm_receipt"),

    /** Обновить данные заявки (треки и комментарии). */
    UPDATE_DETAILS("update_details"),

    /** Закрыть заявку без запуска обмена. */
    CLOSE("close");

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
