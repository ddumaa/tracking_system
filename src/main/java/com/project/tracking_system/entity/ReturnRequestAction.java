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
     * Переводит заявку в режим возврата (отмена обмена с сохранением обращения).
     */
    SET_MODE_RETURN("set_mode_return", "Перевести в возврат", true),

    /**
     * Запускает сценарий обмена для активной заявки возврата.
     */
    SET_MODE_EXCHANGE("set_mode_exchange", "Перевести в обмен", false),

    /**
     * Создаёт новую обменную посылку для обменной заявки.
     */
    CREATE_EXCHANGE_PARCEL("create_exchange_parcel", "Создать обменную посылку", true),

    /**
     * Закрывает заявку без запуска обмена.
     */
    CLOSE_REQUEST("close_request", "Закрыть обращение", false),

    /**
     * Отменяет одобренный обмен.
     */
    CANCEL_EXCHANGE("cancel_exchange", "Отменить обмен", true),

    /**
     * Подтверждает получение возврата магазином вручную.
     */
    CONFIRM_RECEIPT("confirm_receipt", "Подтвердить получение возврата", false),

    /**
     * Обновляет обратный трек и комментарий заявки.
     */
    UPDATE_DETAILS("update_details", "Обновить данные обратной отправки", false);

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
