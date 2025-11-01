package com.project.tracking_system.entity;

import java.util.Objects;

/**
 * Доступные действия с активной заявкой на возврат или обмен.
 * <p>
 * Используются в Telegram-боте и веб-интерфейсе для построения доступных кнопок,
 * а также для сохранения запросов покупателя магазину.
 * </p>
 */
public enum ReturnRequestAction {

    /**
     * Запускает процесс обмена для заявки, находящейся в статусе возврата.
     */
    START_EXCHANGE("start_exchange", "Запустить обмен", false),

    /**
     * Создаёт обменную посылку и фиксирует её привязку к заявке.
     */
    CREATE_EXCHANGE_PARCEL("create_exchange_parcel", "Создать обменную посылку", true),

    /**
     * Отменяет обмен, возвращая заявку к обработке как классического возврата.
     */
    CANCEL_EXCHANGE("cancel_exchange", "Отменить обмен", true),

    /**
     * Переводит обмен обратно в возврат без закрытия заявки.
     */
    REOPEN_RETURN("reopen_return", "Перевести в возврат", true),

    /**
     * Подтверждает приём возврата магазином в ручном режиме.
     */
    CONFIRM_RECEIPT("confirm_receipt", "Подтвердить приём возврата", false),

    /**
     * Обновляет данные обратного трека и комментарий по заявке.
     */
    UPDATE_DETAILS("update_details", "Обновить данные заявки", false),

    /**
     * Закрывает заявку без запуска обмена.
     */
    CLOSE("close", "Закрыть обращение", false);

    private final String code;
    private final String displayName;
    private final boolean exchangeOnly;

    ReturnRequestAction(String code, String displayName, boolean exchangeOnly) {
        this.code = code;
        this.displayName = displayName;
        this.exchangeOnly = exchangeOnly;
    }

    /**
     * Возвращает строковый код действия для сериализации в callback-данные.
     */
    public String getCode() {
        return code;
    }

    /**
     * Возвращает человеко-читаемое название действия.
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * Проверяет, доступно ли действие только для обменных заявок.
     */
    public boolean isExchangeOnly() {
        return exchangeOnly;
    }

    /**
     * Восстанавливает действие по его строковому коду.
     *
     * @param code текстовый код, пришедший из интерфейса
     * @return найденное действие или {@code null}, если код неизвестен
     */
    public static ReturnRequestAction fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (ReturnRequestAction action : values()) {
            if (Objects.equals(action.code, code)) {
                return action;
            }
        }
        return null;
    }
}
