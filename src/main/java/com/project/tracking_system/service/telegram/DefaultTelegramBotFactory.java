package com.project.tracking_system.service.telegram;

import com.project.tracking_system.entity.Store;
import com.project.tracking_system.repository.StoreRepository;
import com.project.tracking_system.service.customer.CustomerTelegramService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.generics.TelegramClient;

/**
 * Реализация фабрики телеграм-ботов.
 */
@Component
@RequiredArgsConstructor
public class DefaultTelegramBotFactory implements TelegramBotFactory {

    private final TelegramClientFactory clientFactory;
    private final CustomerTelegramService telegramService;
    private final StoreRepository storeRepository;

    @Override
    public BuyerTelegramBot create(String token) {
        TelegramClient client = clientFactory.create(token);
        Store store = storeRepository.findByAssignedBotToken(token).orElse(null);
        return new BuyerTelegramBot(store, client, token, telegramService);
    }
}
