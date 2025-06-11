package com.project.tracking_system.entity;

import java.math.BigDecimal;
import java.time.ZonedDateTime;

/**
 * Интерфейс сущностей, поддерживающих установку агрегированных значений статистики.
 */
public interface Aggregatable {
    /**
     * Устанавливает количество отправленных отправлений.
     *
     * @param sent число отправлений
     */
    void setSent(int sent);

    /**
     * Устанавливает количество доставленных отправлений.
     *
     * @param delivered число доставленных
     */
    void setDelivered(int delivered);

    /**
     * Устанавливает количество возвращённых отправлений.
     *
     * @param returned число возвратов
     */
    void setReturned(int returned);

    /**
     * Устанавливает суммарное количество дней доставки.
     *
     * @param sumDeliveryDays общая длительность доставки
     */
    void setSumDeliveryDays(BigDecimal sumDeliveryDays);

    /**
     * Устанавливает суммарное количество дней получения.
     *
     * @param sumPickupDays общая длительность ожидания получения
     */
    void setSumPickupDays(BigDecimal sumPickupDays);

    /**
     * Устанавливает момент последнего обновления.
     *
     * @param updatedAt время обновления
     */
    void setUpdatedAt(ZonedDateTime updatedAt);
}
