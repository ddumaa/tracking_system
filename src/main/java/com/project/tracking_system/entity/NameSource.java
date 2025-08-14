package com.project.tracking_system.entity;

/**
 * Источник данных для имени покупателя.
 */
public enum NameSource {
    /** Имя подтверждено пользователем. */
    USER_CONFIRMED,
    /** Имя передано магазином. */
    MERCHANT_PROVIDED;
}
