package com.project.tracking_system.service.telegram;

import org.springframework.stereotype.Component;
import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient;
import org.telegram.telegrambots.meta.generics.TelegramClient;

/**
 * Реализация фабрики клиентов Telegram на базе OkHttp.
 */
@Component
public class DefaultTelegramClientFactory implements TelegramClientFactory {
    @Override
    public TelegramClient create(String token) {
        return new OkHttpTelegramClient(token);
    }
}
