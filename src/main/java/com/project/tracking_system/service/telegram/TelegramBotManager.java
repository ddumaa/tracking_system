package com.project.tracking_system.service.telegram;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.BotSession;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Сервис управления пользовательскими Telegram-ботами.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TelegramBotManager {

    private final TelegramBotsApi telegramBotsApi;
    private final TelegramBotFactory botFactory;

    /** Сессии ботов по токену. */
    private final Map<String, BotSession> sessions = new ConcurrentHashMap<>();

    /**
     * Регистрирует и запускает Telegram-бота по указанному токену.
     *
     * @param token токен бота
     */
    public synchronized void registerBot(String token) {
        if (token == null || token.isBlank() || sessions.containsKey(token)) {
            return;
        }
        try {
            BuyerTelegramBot bot = botFactory.create(token);
            BotSession session = telegramBotsApi.registerBot(bot);
            session.start();
            sessions.put(token, session);
            log.info("▶️ Пользовательский бот запущен: {}", token);
        } catch (TelegramApiException e) {
            log.error("❌ Ошибка запуска бота {}", token, e);
            throw new IllegalStateException("Не удалось зарегистрировать бота", e);
        }
    }

    /**
     * Останавливает и удаляет бота с указанным токеном.
     *
     * @param token токен бота
     */
    public synchronized void unregisterBot(String token) {
        if (token == null || token.isBlank()) {
            return;
        }
        BotSession session = sessions.remove(token);
        if (session != null) {
            session.stop();
            log.info("⏹ Пользовательский бот остановлен: {}", token);
        }
    }
}
