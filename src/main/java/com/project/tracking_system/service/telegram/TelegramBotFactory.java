package com.project.tracking_system.service.telegram;

import com.project.tracking_system.service.telegram.BuyerTelegramBot;

/**
 * Фабрика телеграм-ботов для покупателей.
 */
public interface TelegramBotFactory {
    /**
     * Создает экземпляр бота по заданному токену.
     *
     * @param token токен Telegram-бота
     * @return настроенный бот
     */
    BuyerTelegramBot create(String token);
}
