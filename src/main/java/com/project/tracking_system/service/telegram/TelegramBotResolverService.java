package com.project.tracking_system.service.telegram;

import com.project.tracking_system.entity.Store;
import com.project.tracking_system.entity.StoreTelegramSettings;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Сервис определения Telegram-бота для магазина.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TelegramBotResolverService {

    private final TelegramClient systemTelegramClient;
    private final TelegramClientFactory telegramClientFactory;

    /** Кэш клиентов Telegram по токену пользовательского бота. */
    private final Map<String, TelegramClient> clientCache = new ConcurrentHashMap<>();

    /**
     * Получить {@link TelegramClient} для уведомлений магазина.
     *
     * @param store магазин
     * @return клиент Telegram соответствующего бота
     */
    public TelegramClient resolveBotForStore(Store store) {
        if (store == null) {
            return systemTelegramClient;
        }

        StoreTelegramSettings settings = store.getTelegramSettings();
        if (settings == null) {
            return systemTelegramClient;
        }

        String token = settings.getBotToken();
        if (token == null || token.isBlank()) {
            return systemTelegramClient;
        }

        // Создаем или возвращаем из кэша клиента для пользовательского токена
        return clientCache.computeIfAbsent(token, telegramClientFactory::create);
    }

    /**
     * Удалить клиент из кэша по токену.
     *
     * @param token токен бота
     */
    public void invalidateClient(String token) {
        if (token != null && !token.isBlank()) {
            clientCache.remove(token);
        }
    }

    /**
     * Удалить клиент из кэша по настройкам магазина.
     *
     * @param settings настройки Telegram
     */
    public void invalidateClient(StoreTelegramSettings settings) {
        if (settings != null) {
            invalidateClient(settings.getBotToken());
        }
    }
}
