package com.project.tracking_system.entity;

/**
 * Статус события изменения ФИО покупателя.
 */
public enum CustomerNameEventStatus {
    /** Текущее актуальное событие. */
    ACTIVE,
    /** Событие устарело из-за появления нового. */
    SUPERSEDED
}
