package com.project.tracking_system.model.customer;

/**
 * Варианты действий покупателя при подтверждении ФИО.
 */
public enum CustomerNameDecision {
    /** Покупатель подтверждает введённые данные. */
    YES,
    /** Покупатель отклоняет предложенный вариант. */
    NO,
    /** Покупатель отправляет своё исправленное ФИО. */
    CHANGE
}
