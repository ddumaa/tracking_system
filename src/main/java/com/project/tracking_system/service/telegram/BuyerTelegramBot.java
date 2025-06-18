package com.project.tracking_system.service.telegram;

import com.project.tracking_system.service.customer.CustomerRegistrationService;
import com.project.tracking_system.utils.PhoneUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient;
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

/**
 * Telegram-бот для покупателей.
 */
@Component
@Slf4j
public class BuyerTelegramBot implements SpringLongPollingBot, LongPollingSingleThreadUpdateConsumer {

    private final TelegramClient telegramClient;
    private final CustomerRegistrationService registrationService;
    private final String botToken;


    /**
     * Создаёт телеграм-бота для покупателей.
     *
     * @param registrationService сервис регистрации покупателей
     */
    public BuyerTelegramBot(@Value("${telegram.bot.token}") String token,
                            CustomerRegistrationService registrationService) {
        this.botToken = token;
        this.registrationService = registrationService;
        this.telegramClient = new OkHttpTelegramClient(token);
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

            if (message.hasText() && "/start".equals(message.getText())) {
                log.info("✅ Команда /start получена от {}", message.getChatId());
                sendSharePhoneKeyboard(message.getChatId());
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

    private void handleContact(Long chatId, Contact contact) {
        String rawPhone = contact.getPhoneNumber();
        String phone = PhoneUtils.normalizePhone(rawPhone);

        try {
            registrationService.linkTelegramToCustomer(chatId, phone);
            SendMessage confirm = new SendMessage(chatId.toString(), "✅ Номер сохранён. Спасибо!");

            telegramClient.execute(confirm);
        } catch (Exception e) {
            log.error("❌ Ошибка регистрации телефона {} для чата {}", phone, chatId, e);
        }
    }
}