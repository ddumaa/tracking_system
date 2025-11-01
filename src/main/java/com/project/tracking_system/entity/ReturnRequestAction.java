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
     * Переводит текущую заявку в классический возврат, если ранее был выбран обмен.
     */
    SET_MODE_RETURN("set_mode_return", "Перевести в возврат", true),

    /**
     * Переводит заявку в режим обмена, инициируя встречную отправку магазина.
     */
    SET_MODE_EXCHANGE("set_mode_exchange", "Перевести в обмен", false),

    /**
     * Помечает, что покупатель отправил исходную посылку обратно в магазин.
     */
    MARK_OUTBOUND_SENT("mark_outbound_sent", "Отметить исходящую отправку", false),

    /**
     * Фиксирует прибытие исходящей посылки в точку приёма магазина.
     */
    MARK_INBOUND_ARRIVED("mark_inbound_arrived", "Отметить прибытие возврата", false),

    /**
     * Подтверждает, что возврат был выдан сотруднику склада магазина.
     */
    MARK_INBOUND_PICKED_UP("mark_inbound_picked_up", "Подтвердить выдачу возврата", false),

    /**
     * Регистрирует данные обменной посылки, которую магазин подготовил покупателю.
     */
    REGISTER_EXCHANGE_PARCEL("register_exchange_parcel", "Зарегистрировать обменную посылку", true),

    /**
     * Помечает отправку обменной посылки покупателю.
     */
    MARK_EXCHANGE_SENT("mark_exchange_sent", "Отправить обменную посылку", true),

    /**
     * Отмечает успешную доставку обменной посылки покупателю.
     */
    MARK_EXCHANGE_DELIVERED("mark_exchange_delivered", "Подтвердить доставку обмена", true),

    /**
     * Обновляет данные обратного трека и комментарий по заявке.
     */
    UPDATE_REVERSE_TRACK("update_reverse_track", "Обновить обратный трек", false),

    /**
     * Закрывает заявку после завершения обработки обращения.
     */
    CLOSE_REQUEST("close_request", "Закрыть обращение", false);

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
