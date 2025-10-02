package com.project.tracking_system.entity;

/**
 * Статусы заявки на возврат/обмен в рамках эпизода заказа.
 */
public enum OrderReturnRequestStatus {

    /**
     * Заявка зарегистрирована и ожидает решения по дальнейшим действиям.
     */
    REGISTERED("Зарегистрирована"),

    /**
     * Принято решение отправить обменную посылку.
     */
    EXCHANGE_APPROVED("Обмен запущен"),

    /**
     * Заявка закрыта без запуска обмена.
     */
    CLOSED_NO_EXCHANGE("Закрыта без обмена");

    private final String displayName;

    OrderReturnRequestStatus(String displayName) {
        this.displayName = displayName;
    }

    /**
     * Возвращает локализованное название статуса для отображения на UI.
     */
    public String getDisplayName() {
        return displayName;
    }
}

