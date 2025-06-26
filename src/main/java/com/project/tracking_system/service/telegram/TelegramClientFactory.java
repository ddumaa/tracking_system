package com.project.tracking_system.service.telegram;

import org.telegram.telegrambots.meta.generics.TelegramClient;

/**
 * Фабрика клиентов Telegram для работы с разными токенами.
 */
public interface TelegramClientFactory {
    /**
     * Создаёт клиент Telegram для указанного токена.
     *
     * @param token токен бота
     * @return клиент Telegram
     */
    TelegramClient create(String token);
}
