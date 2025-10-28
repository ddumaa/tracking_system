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
     * Отмена активной заявки на возврат без запуска обмена.
     */
    CANCEL_RETURN("cancel", "Отменить возврат", false),

    /**
     * Отмена одобренного обмена до отправки обменной посылки.
     */
    CANCEL_EXCHANGE("cancel_exchange", "Отменить обмен", true),

    /**
     * Перевод одобренного обмена обратно в возврат.
     */
    CONVERT_TO_RETURN("convert", "Перевести в возврат", true);

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
