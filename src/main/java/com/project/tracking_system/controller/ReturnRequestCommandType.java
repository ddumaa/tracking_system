package com.project.tracking_system.controller;

import java.util.Arrays;

/**
 * Перечисление доступных команд управления заявками на возврат.
 */
enum ReturnRequestCommandType {

    /** Перевести заявку в режим обмена. */
    START_EXCHANGE("start_exchange"),

    /** Создать обменную посылку. */
    CREATE_EXCHANGE_PARCEL("create_exchange_parcel"),

    /** Закрыть заявку без запуска обмена. */
    CLOSE("close"),

    /** Подтвердить получение возврата магазином. */
    CONFIRM_RECEIPT("confirm_receipt"),

    /** Обновить данные обратной отправки и комментарий. */
    UPDATE_DETAILS("update_details"),

    /** Перевести обмен обратно в возврат. */
    REOPEN("reopen"),

    /** Отменить обмен после запуска. */
    CANCEL_EXCHANGE("cancel_exchange");

    private final String code;

    ReturnRequestCommandType(String code) {
        this.code = code;
    }

    /**
     * Восстанавливает тип команды по строковому коду.
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
