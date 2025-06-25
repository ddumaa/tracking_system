package com.project.tracking_system.entity;

/**
 * Статус посылки, предназначенный для отображения покупателю.
 * Содержит шаблон сообщения и читабельное название на русском языке.
 */
public enum BuyerStatus {

    /** Заказ зарегистрирован. */
    REGISTERED("Зарегистрирован", "Ваш заказ %s из магазина %s зарегистрирован и скоро будет отправлен."),
    /** Посылка находится в пути. */
    IN_TRANSIT("В пути", "Посылка %s из магазина %s находится в пути."),
    /** Посылка ожидает получения. */
    WAITING("Ожидает получения", "Посылка %s из магазина %s прибыла и ждёт вас в пункте выдачи."),
    /** Посылка получена покупателем. */
    DELIVERED("Получена", "Посылка %s из магазина %s получена. Спасибо за покупку!"),
    /** Посылка возвращается отправителю. */
    RETURNED("Возврат", "Посылка %s из магазина %s уже возвращается отправителю.");

    private final String displayName;
    private final String messageTemplate;

    BuyerStatus(String displayName, String messageTemplate) {
        this.displayName = displayName;
        this.messageTemplate = messageTemplate;
    }

    /**
     * Возвращает русскоязычное название статуса.
     *
     * @return читаемое название статуса
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * Формирует сообщение на основе переданных данных.
     *
     * @param track трек-номер посылки
     * @param store название магазина
     * @return сформированное сообщение
     */
    public String formatMessage(String track, String store) {
        return String.format(messageTemplate, track, store);
    }
}
