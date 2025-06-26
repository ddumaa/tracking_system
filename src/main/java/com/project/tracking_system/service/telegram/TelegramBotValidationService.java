package com.project.tracking_system.service.telegram;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.methods.GetMe;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.generics.TelegramClient;

/**
 * Сервис проверки токенов Telegram-бота.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TelegramBotValidationService {

    private final TelegramClientFactory telegramClientFactory;

    /**
     * Проверяет токен бота методом GetMe и возвращает имя бота.
     *
     * @param token токен бота
     * @return имя пользователя Telegram-бота
     */
    public String validateToken(String token) {
        try {
            TelegramClient client = telegramClientFactory.create(token);
            User me = client.execute(new GetMe());
            log.info("✅ Токен Telegram валиден, бот: {}", me.getUserName());
            return me.getUserName();
        } catch (Exception e) {
            log.error("❌ Ошибка проверки токена Telegram", e);
            throw new IllegalArgumentException("Неверный токен бота", e);
        }
    }
}
