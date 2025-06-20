package com.project.tracking_system.service.telegram;

import com.project.tracking_system.entity.Customer;
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

/**
 * Telegram-бот для покупателей.
 */
@Component
@Slf4j
public class BuyerTelegramBot implements SpringLongPollingBot, LongPollingSingleThreadUpdateConsumer {

    private final TelegramClient telegramClient;
    private final CustomerTelegramService telegramService;
    private final String botToken;

    /**
     * Создаёт телеграм-бота для покупателей.
     *
     * @param telegramClient       клиент Telegram, предоставляемый Spring
     * @param token                токен бота (может отсутствовать)
     * @param telegramService      сервис привязки покупателей к Telegram
     */
    public BuyerTelegramBot(TelegramClient telegramClient,
                            @Value("${telegram.bot.token:}") String token,
                            CustomerTelegramService telegramService) {
        this.telegramClient = telegramClient;
        this.botToken = token;
        this.telegramService = telegramService;
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
                if ("/start".equals(text)) {
                    log.info("✅ Команда /start получена от {}", message.getChatId());
                    sendSharePhoneKeyboard(message.getChatId());

                    // 🔽 Добавить отображение текущих настроек, если юзер уже привязан
                    Optional<Customer> optional = telegramService.findByChatId(message.getChatId());
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

        try {
            Customer customer = telegramService.linkTelegramToCustomer(phone, chatId);
            if (!customer.isTelegramConfirmed()) {
                SendMessage confirm = new SendMessage(chatId.toString(), "✅ Номер сохранён. Спасибо!");
                telegramClient.execute(confirm);
                telegramService.confirmTelegram(customer);
                telegramService.notifyActualStatuses(customer);
                sendNotificationsKeyboard(chatId, true);
            }
        } catch (Exception e) {
            log.error("❌ Ошибка регистрации телефона {} для чата {}", phone, chatId, e);
        }
    }
}