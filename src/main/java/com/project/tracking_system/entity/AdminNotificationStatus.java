package com.project.tracking_system.entity;

/**
 * Статус административного уведомления.
 */
public enum AdminNotificationStatus {
    /**
     * Уведомление активно и должно отображаться пользователям.
     */
    ACTIVE,
    /**
     * Уведомление не активно и хранится в истории.
     */
    INACTIVE
}
