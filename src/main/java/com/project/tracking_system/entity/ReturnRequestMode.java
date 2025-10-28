package com.project.tracking_system.entity;

import java.util.Objects;

/**
 * Режим обработки заявки на возврат/обмен.
 * <p>
 * Определяет сценарий, по которому движется заявка: классический возврат
 * или обмен с отправкой новой посылки. Значение хранится в базе и используется
 * для построения интерфейсов Telegram и веб-приложения.
 * </p>
 */
public enum ReturnRequestMode {

    /**
     * Классический возврат товара без оформления обменной посылки.
     */
    RETURN("Возврат"),

    /**
     * Заявка, по которой требуется отправить покупателю обменную посылку.
     */
    EXCHANGE("Обмен");

    private final String displayName;

    ReturnRequestMode(String displayName) {
        this.displayName = displayName;
    }

    /**
     * Возвращает локализованное название режима для отображения в интерфейсе.
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * Проверяет, относится ли режим к обмену товара.
     */
    public boolean isExchange() {
        return this == EXCHANGE;
    }

    /**
     * Проверяет, относится ли режим к классическому возврату.
     */
    public boolean isReturn() {
        return this == RETURN;
    }

    /**
     * Определяет режим заявки по её признакам и статусу.
     *
     * @param request заявка на возврат/обмен
     * @return режим обработки заявки
     */
    public static ReturnRequestMode from(OrderReturnRequest request) {
        if (request == null) {
            return RETURN;
        }
        if (request.isExchangeApproved() || request.isExchangeRequested()) {
            return EXCHANGE;
        }
        return RETURN;
    }

    /**
     * Преобразует строковое значение, считанное из базы, в режим заявки.
     * Метод защищает от {@code null} и неизвестных значений, возвращая режим возврата по умолчанию.
     */
    public static ReturnRequestMode fromDatabaseValue(String value) {
        if (value == null || value.isBlank()) {
            return RETURN;
        }
        for (ReturnRequestMode mode : values()) {
            if (Objects.equals(mode.name(), value)) {
                return mode;
            }
        }
        return RETURN;
    }
}
