package com.project.tracking_system.dto;

/**
 * Состояние этапа жизненного цикла заказа в модальном окне трека.
 */
public enum TrackLifecycleStageState {

    /**
     * Этап ещё не начался.
     */
    PLANNED,

    /**
     * Этап находится в работе.
     */
    IN_PROGRESS,

    /**
     * Этап успешно завершён.
     */
    COMPLETED
}
