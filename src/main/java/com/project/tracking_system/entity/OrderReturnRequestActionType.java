package com.project.tracking_system.entity;

/**
 * Типы запросов к магазину по заявке на обмен или возврат.
 */
public enum OrderReturnRequestActionType {
    /** Запрос на отмену обмена после отправки обменной посылки. */
    CANCEL_EXCHANGE,
    /** Запрос на перевод обмена обратно в возврат после отправки посылки. */
    CONVERT_TO_RETURN
}
