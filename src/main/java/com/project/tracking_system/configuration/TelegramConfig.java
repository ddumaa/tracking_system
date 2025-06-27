package com.project.tracking_system.configuration;

import com.project.tracking_system.service.telegram.BuyerTelegramBot;
import com.project.tracking_system.service.telegram.TelegramBotFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient;
import org.telegram.telegrambots.meta.generics.TelegramClient;

/**
 * @author Dmitriy Anisimov
 * @date 18.06.2025
 */
@Configuration
public class TelegramConfig {

    @Value("${telegram.bot.token}")
    private String botToken;

    /**
     * Создает клиента Telegram для взаимодействия с ботом.
     * <p>
     * Возвращает настроенный {@link TelegramClient}, использующий токен бота.
     * </p>
     *
     * @return экземпляр {@link TelegramClient}
     */
    @Bean
    public TelegramClient telegramClient() {
        return new OkHttpTelegramClient(botToken);
    }

    /**
     * Создает системного Telegram-бота.
     *
     * @param factory фабрика ботов
     * @return экземпляр {@link BuyerTelegramBot}
     */
    @Bean
    public BuyerTelegramBot buyerTelegramBot(TelegramBotFactory factory) {
        return factory.create(botToken);
    }

}