package com.project.tracking_system.service.order;

/**
 * Тип события трекинга, которое влияет на стадии обработки возврата или обмена.
 */
public enum ReturnTrackingEventType {
    /** Возврат прибыл на склад магазина и ожидает обработки. */
    RETURN_ARRIVED_TO_STORE,
    /** Магазин получил возврат и завершил обработку поступления. */
    RETURN_PICKED_UP_BY_STORE,
    /** Обменная посылка успешно доставлена покупателю. */
    EXCHANGE_DELIVERED_TO_CUSTOMER
}
