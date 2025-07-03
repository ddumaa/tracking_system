package com.project.tracking_system.model.subscription;

import lombok.Getter;

/**
 * Перечисление ключей доступных функций тарифных планов.
 */
@Getter
public enum FeatureKey {

    /**
     * Массовое обновление треков.
     */
    BULK_UPDATE("bulkUpdate"),

    /**
     * Отправка Telegram-уведомлений.
     */
    TELEGRAM_NOTIFICATIONS("telegramNotifications"),

    /**
     * Автоматическое обновление треков.
     */
    AUTO_UPDATE("autoUpdate"),

    /**
     * Индивидуальные уведомления с собственными шаблонами.
     */
    CUSTOM_NOTIFICATIONS("customNotifications");

    /**
     * -- GETTER --
     *  Возвращает строковое представление ключа.
     *
     * @return ключ функции
     */
    private final String key;

    FeatureKey(String key) {
        this.key = key;
    }

}