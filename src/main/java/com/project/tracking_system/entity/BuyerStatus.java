package com.project.tracking_system.entity;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * @author Dmitriy Anisimov
 * @date 19.06.2025
 */
@Getter
@AllArgsConstructor
public enum BuyerStatus {

    REGISTERED("Ваш заказ %s из магазина %s зарегистрирован и скоро будет отправлен."),
    IN_TRANSIT("Посылка %s из магазина %s находится в пути."),
    WAITING("Посылка %s из магазина %s прибыла и ждёт вас в пункте выдачи."),
    DELIVERED("Посылка %s из магазина %s получена. Спасибо за покупку!"),
    RETURNED("Посылка %s из магазина %s возвращена отправителю.");

    private final String messageTemplate;

    public String formatMessage(String track, String store) {
        return String.format(messageTemplate, track, store);
    }

}