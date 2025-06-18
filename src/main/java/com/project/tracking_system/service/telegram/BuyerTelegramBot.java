package com.project.tracking_system.service.telegram;

import com.project.tracking_system.service.customer.CustomerRegistrationService;
import com.project.tracking_system.utils.PhoneUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
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
@Slf4j
public class BuyerTelegramBot {

    private final CustomerRegistrationService registrationService;

    @Value("${telegrambots.bots[0].botUsername}")
    private String botUsername;

    @Value("${telegrambots.bots[0].botToken}")
    private String botToken;

    /**
     * Создаёт телеграм-бота для покупателей.
     *
     * @param registrationService сервис регистрации покупателей
     */
    public BuyerTelegramBot(CustomerRegistrationService registrationService) {
        this.registrationService = registrationService;
    }

    /**
     * Возвращает имя бота в Telegram.
     *
     * @return имя бота
     */
    @Override
    public String getBotUsername() {
        return botUsername;
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

        log.info("🔁 Обработка обновления: {}", update);

        if (update.hasMessage()) {
            String text = update.getMessage().getText();
            if (text != null && text.startsWith("/start")) {
                log.info("✅ Команда /start получена, отправляем клавиатуру.");
                sendSharePhoneKeyboard(update.getMessage().getChatId());
                return;
            }

            Contact contact = update.getMessage().getContact();
            log.info("📥 Получена команда: {}", update.getMessage().getText());

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
        markup.setOneTimeKeyboard(true);

        SendMessage message = new SendMessage(chatId.toString(), "👋 Чтобы получать уведомления о посылках, поделитесь номером телефона.");
        message.setReplyMarkup(markup);

        log.info("📨 Отправляем клавиатуру с запросом номера в чат {}", chatId);

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
        try {
            // Пытаемся привязать чат к покупателю
            registrationService.linkTelegramToCustomer(chatId, phone);

            // Отправляем подтверждение только при успешной привязке
            SendMessage confirm = new SendMessage(chatId.toString(), "Номер сохранён. Спасибо!");
            try {
                execute(confirm);
            } catch (TelegramApiException e) {
                log.error("Не удалось отправить подтверждение", e);
            }
        } catch (Exception e) {
            // Если регистрация не удалась, сообщение не отправляем
            log.error("Ошибка привязки телефона {} к чату {}", phone, chatId, e);
        }
    }
}