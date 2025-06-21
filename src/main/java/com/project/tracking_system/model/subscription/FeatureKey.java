package com.project.tracking_system.model.subscription;

/**
 * Перечисление ключей доступных функций тарифных планов.
 */
public enum FeatureKey {

    /**
     * Массовое обновление треков.
     */
    BULK_UPDATE("bulkUpdate"),

    /**
     * Отправка Telegram-уведомлений.
     */
    TELEGRAM_NOTIFICATIONS("telegramNotifications");

    private final String key;

    FeatureKey(String key) {
        this.key = key;
    }

    /**
     * Возвращает строковое представление ключа.
     *
     * @return ключ функции
     */
    public String getKey() {
        return key;
    }
}
