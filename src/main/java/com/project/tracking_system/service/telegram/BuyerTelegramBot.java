package com.project.tracking_system.service.telegram;

import com.project.tracking_system.entity.CustomerTelegramLink;
import com.project.tracking_system.entity.Store;
import com.project.tracking_system.repository.StoreRepository;
import com.project.tracking_system.service.customer.CustomerTelegramService;
import com.project.tracking_system.utils.PhoneUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.longpolling.interfaces.LongPollingUpdateConsumer;
import org.telegram.telegrambots.longpolling.starter.SpringLongPollingBot;
import org.telegram.telegrambots.longpolling.util.LongPollingSingleThreadUpdateConsumer;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Contact;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.util.List;
import java.util.Optional;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Telegram-бот для покупателей.
 */
@Component
@Slf4j
public class BuyerTelegramBot implements SpringLongPollingBot, LongPollingSingleThreadUpdateConsumer {

    private final TelegramClient telegramClient;
    private final CustomerTelegramService telegramService;
    private final StoreRepository storeRepository;
    private final String botToken;

    /** Связь чата и выбранного магазина для ожидания контакта. */
    private final Map<Long, Long> chatStoreContext = new ConcurrentHashMap<>();

    /**
     * Создаёт телеграм-бота для покупателей.
     *
     * @param telegramClient       клиент Telegram, предоставляемый Spring
     * @param token                токен бота (может отсутствовать)
     * @param telegramService      сервис привязки покупателей к Telegram
     */
    public BuyerTelegramBot(TelegramClient telegramClient,
                            @Value("${telegram.bot.token:}") String token,
                            CustomerTelegramService telegramService,
                            StoreRepository storeRepository) {
        this.telegramClient = telegramClient;
        this.botToken = token;
        this.telegramService = telegramService;
        this.storeRepository = storeRepository;
    }

    /**
     * Возвращает токен для доступа к API Telegram.
     *
     * @return токен бота
     */
    @Override
    public String getBotToken() {
        return botToken;
    }

    @Override
    public LongPollingUpdateConsumer getUpdatesConsumer() {
        return this;
    }

    /**
     * Новый метод, который вызывает TelegramBots v9
     */
    @Override
    public void consume(Update update) {
        log.info("📩 Обновление: {}", update);

        if (update.hasMessage()) {
            var message = update.getMessage();

            if (message.hasText()) {
                String text = message.getText();
                if (text.startsWith("/start")) {
                    Long storeId = parseStoreId(text);
                    if (storeId != null) {
                        chatStoreContext.put(message.getChatId(), storeId);
                    }

                    log.info("✅ Команда /start получена от {} для магазина {}", message.getChatId(), storeId);
                    sendSharePhoneKeyboard(message.getChatId());

                    Optional<CustomerTelegramLink> optional =
                            (storeId == null)
                                    ? telegramService.findByChatId(message.getChatId())
                                    : telegramService.findByChatIdAndStore(message.getChatId(), storeId);

                    if (optional.isPresent() && optional.get().isTelegramConfirmed()) {
                        boolean enabled = optional.get().isNotificationsEnabled();
                        sendNotificationsKeyboard(message.getChatId(), enabled);
                    }
                }
                if ("/stop".equals(text) || "/unsubscribe".equals(text)) {
                    log.info("🔕 Команда {} получена от {}", text, message.getChatId());
                    boolean disabled = telegramService.disableNotifications(message.getChatId());
                    if (disabled) {
                        SendMessage confirm = new SendMessage(message.getChatId().toString(),
                                "🔕 Уведомления отключены. Чтобы возобновить их, снова отправьте /start.");
                        try {
                            telegramClient.execute(confirm);
                        } catch (TelegramApiException e) {
                            log.error("❌ Ошибка отправки подтверждения", e);
                        }
                    }
                }
                if ("🔕 Отключить уведомления".equals(text)) {
                    boolean disabled = telegramService.disableNotifications(message.getChatId());
                    if (disabled) {
                        sendNotificationsKeyboard(message.getChatId(), false);
                    }
                }
                if ("🔔 Включить уведомления".equals(text)) {
                    boolean enabled = telegramService.enableNotifications(message.getChatId());
                    if (enabled) {
                        sendNotificationsKeyboard(message.getChatId(), true);
                    }
                }
            }

            if (message.hasContact()) {
                handleContact(message.getChatId(), message.getContact());
            }
        }
    }

    private void sendSharePhoneKeyboard(Long chatId) {
        KeyboardButton button = new KeyboardButton("📱 Поделиться номером");
        button.setRequestContact(true);
        KeyboardRow row = new KeyboardRow(List.of(button));
        ReplyKeyboardMarkup markup = new ReplyKeyboardMarkup(List.of(row));
        markup.setResizeKeyboard(true);
        markup.setOneTimeKeyboard(true);

        SendMessage message = new SendMessage(chatId.toString(), "👋 Чтобы получать уведомления о посылках, поделитесь номером телефона.");
        message.setReplyMarkup(markup);

        try {
            telegramClient.execute(message);
        } catch (TelegramApiException e) {
            log.error("❌ Ошибка отправки клавиатуры", e);
        }
    }

    private void sendNotificationsKeyboard(Long chatId, boolean enabled) {
        String buttonText = enabled ? "🔕 Отключить уведомления"
                : "🔔 Включить уведомления";

        KeyboardButton button = new KeyboardButton(buttonText);
        KeyboardRow row = new KeyboardRow(List.of(button));
        ReplyKeyboardMarkup markup = new ReplyKeyboardMarkup(List.of(row));
        markup.setResizeKeyboard(true);
        markup.setOneTimeKeyboard(true);

        SendMessage message = new SendMessage(chatId.toString(), "🔔 Настройки уведомлений");
        message.setReplyMarkup(markup);

        try {
            telegramClient.execute(message);
        } catch (TelegramApiException e) {
            log.error("❌ Ошибка отправки клавиатуры уведомлений", e);
        }
    }

    private void handleContact(Long chatId, Contact contact) {
        String rawPhone = contact.getPhoneNumber();
        String phone = PhoneUtils.normalizePhone(rawPhone);

        Long storeId = chatStoreContext.remove(chatId);
        Store store = null;
        if (storeId != null) {
            store = storeRepository.findStoreById(storeId);
        }

        try {
            CustomerTelegramLink link =
                    store != null
                            ? telegramService.linkTelegramToCustomer(phone, store, chatId)
                            : telegramService.linkTelegramToCustomer(phone, chatId);
            if (!link.isTelegramConfirmed()) {
                SendMessage confirm = new SendMessage(chatId.toString(), "✅ Номер сохранён. Спасибо!");
                telegramClient.execute(confirm);
                telegramService.confirmTelegram(link);
                telegramService.notifyActualStatuses(link);
                sendNotificationsKeyboard(chatId, true);
            }
        } catch (Exception e) {
            log.error("❌ Ошибка регистрации телефона {} для чата {}", phone, chatId, e);
        }
    }

    // Извлекаем идентификатор магазина из параметров команды /start
    private Long parseStoreId(String text) {
        if (text == null || !text.startsWith("/start")) {
            return null;
        }
        String[] parts = text.split(" ", 2);
        if (parts.length < 2) {
            return null;
        }
        try {
            return Long.parseLong(parts[1]);
        } catch (NumberFormatException e) {
            log.warn("⚠ Не удалось разобрать storeId из '{}'", text);
            return null;
        }
    }
}