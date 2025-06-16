package com.project.tracking_system.service.telegram;

import com.project.tracking_system.service.customer.CustomerRegistrationService;
import com.project.tracking_system.utils.PhoneUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Contact;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.util.List;

/**
 * Telegram-бот для покупателей.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class BuyerTelegramBot extends TelegramLongPollingBot {

    private final CustomerRegistrationService registrationService;
    private final String botToken;

    /**
     * Создать экземпляр бота.
     *
     * @param registrationService сервис регистрации покупателей
     * @param botToken            токен бота
     */
    public BuyerTelegramBot(CustomerRegistrationService registrationService,
                            @Value("${telegram.bot.token}") String botToken) {
        this.registrationService = registrationService;
        this.botToken = botToken;
    }

    /**
     * Возвращает имя бота в Telegram.
     *
     * @return имя бота
     */
    @Override
    public String getBotUsername() {
        return "BuyerAssistantBot";
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

    /**
     * Обрабатывает входящие обновления от Telegram.
     *
     * @param update объект обновления
     */
    @Override
    public void onUpdateReceived(Update update) {
        if (update.hasMessage()) {
            if (update.getMessage().hasText() && "/start".equals(update.getMessage().getText())) {
                sendSharePhoneKeyboard(update.getMessage().getChatId());
                return;
            }

            Contact contact = update.getMessage().getContact();
            if (contact != null) {
                handleContact(update);
            }
        }
    }

    private void sendSharePhoneKeyboard(Long chatId) {
        KeyboardButton button = new KeyboardButton("\uD83D\uDCF1 Поделиться номером");
        button.setRequestContact(true);
        KeyboardRow row = new KeyboardRow(List.of(button));
        ReplyKeyboardMarkup markup = new ReplyKeyboardMarkup(List.of(row));
        markup.setResizeKeyboard(true);

        SendMessage message = new SendMessage(chatId.toString(), "Поделитесь номером телефона");
        message.setReplyMarkup(markup);
        try {
            execute(message);
        } catch (TelegramApiException e) {
            log.error("Не удалось отправить сообщение", e);
        }
    }

    private void handleContact(Update update) {
        String rawPhone = update.getMessage().getContact().getPhoneNumber();
        String phone = PhoneUtils.normalizePhone(rawPhone);
        Long chatId = update.getMessage().getChatId();
        registrationService.linkTelegramToCustomer(chatId, phone);

        SendMessage confirm = new SendMessage(chatId.toString(), "Номер сохранён. Спасибо!");
        try {
            execute(confirm);
        } catch (TelegramApiException e) {
            log.error("Не удалось отправить подтверждение", e);
        }
    }
}