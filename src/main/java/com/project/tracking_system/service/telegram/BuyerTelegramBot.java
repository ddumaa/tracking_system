package com.project.tracking_system.service.telegram;

import com.project.tracking_system.entity.Customer;
import com.project.tracking_system.entity.NameSource;
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
    private final String botToken;
    private final Map<Long, Boolean> awaitingName = new ConcurrentHashMap<>();

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
                Long chatId = message.getChatId();

                // Проверка режима ожидания ввода ФИО
                if (awaitingName.remove(chatId) != null) {
                    boolean saved = telegramService.updateNameFromTelegram(chatId, text.trim());
                    String reply = saved ? "✅ ФИО сохранено и подтверждено" : "⚠️ Не удалось сохранить ФИО";
                    sendSimpleMessage(chatId, reply);
                    return;
                }

                if ("/start".equals(text)) {
                    log.info("✅ Команда /start получена от {}", chatId);
                    Optional<Customer> optional = telegramService.findByChatId(chatId);
                    if (optional.isEmpty()) {
                        sendSharePhoneKeyboard(chatId);
                        return;
                    }
                    Customer customer = optional.get();
                    sendNotificationsKeyboard(chatId, customer.isNotificationsEnabled());
                    if (customer.getFullName() != null) {
                        if (customer.getNameSource() != NameSource.USER_CONFIRMED) {
                            sendNameConfirmation(chatId, customer.getFullName());
                        }
                    } else {
                        promptForName(chatId);
                    }
                }
                else if ("/stop".equals(text) || "/unsubscribe".equals(text)) {
                    log.info("🔕 Команда {} получена от {}", text, chatId);
                    boolean disabled = telegramService.disableNotifications(chatId);
                    if (disabled) {
                        SendMessage confirm = new SendMessage(chatId.toString(),
                                "🔕 Уведомления отключены. Чтобы возобновить их, снова отправьте /start.");
                        try {
                            telegramClient.execute(confirm);
                        } catch (TelegramApiException e) {
                            log.error("❌ Ошибка отправки подтверждения", e);
                        }
                    }
                }
                else if ("🔕 Отключить уведомления".equals(text)) {
                    boolean disabled = telegramService.disableNotifications(chatId);
                    if (disabled) {
                        sendNotificationsKeyboard(chatId, false);
                    }
                }
                else if ("🔔 Включить уведомления".equals(text)) {
                    boolean enabled = telegramService.enableNotifications(chatId);
                    if (enabled) {
                        sendNotificationsKeyboard(chatId, true);
                    }
                }
                else if ("Верно".equalsIgnoreCase(text)) {
                    if (telegramService.confirmName(chatId)) {
                        sendSimpleMessage(chatId, "✅ Спасибо, данные подтверждены");
                    }
                }
                else if ("Неверно".equalsIgnoreCase(text)) {
                    telegramService.markNameUnconfirmed(chatId);
                    promptForName(chatId);
                }
                else if ("Изменить".equalsIgnoreCase(text)) {
                    promptForName(chatId);
                }
                // Покупатель запросил статистику о своих посылках
                if ("/stats".equals(text) || "📊 Моя статистика".equals(text)) {
                    telegramService.getStatistics(chatId)
                            .ifPresent(stats -> {
                                String stores = stats.getStoreNames().isEmpty()
                                        ? "-" : String.join(", ", stats.getStoreNames());
                                String reply = String.format(
                                        "\uD83D\uDCCA Ваша статистика:\n" +
                                                "Забрано: %d\n" +
                                                "Не забрано: %d\n" +
                                                "Магазины: %s\n" +
                                                "Репутация: %s",
                                        stats.getPickedUpCount(),
                                        stats.getReturnedCount(),
                                        stores,
                                        stats.getReputation().getDisplayName()
                                );
                                SendMessage msg = new SendMessage(chatId.toString(), reply);
                                try {
                                    telegramClient.execute(msg);
                                } catch (TelegramApiException e) {
                                    log.error("❌ Ошибка отправки статистики", e);
                                }
                            });
                }
            }

            if (message.hasContact()) {
                handleContact(message.getChatId(), message.getContact());
            }
        }
    }

    /**
     * Попросить покупателя отправить номер телефона для привязки Telegram.
     *
     * @param chatId идентификатор чата Telegram
     */
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

    /**
     * Отправить клавиатуру для управления уведомлениями и просмотра статистики.
     *
     * @param chatId  идентификатор чата Telegram
     * @param enabled включены ли уведомления в данный момент
     */
    private void sendNotificationsKeyboard(Long chatId, boolean enabled) {
        String buttonText = enabled ? "🔕 Отключить уведомления"
                : "🔔 Включить уведомления";

        KeyboardButton notifyButton = new KeyboardButton(buttonText);
        KeyboardButton statsButton = new KeyboardButton("📊 Моя статистика");
        KeyboardRow firstRow = new KeyboardRow(List.of(notifyButton));
        KeyboardRow secondRow = new KeyboardRow(List.of(statsButton));
        ReplyKeyboardMarkup markup = new ReplyKeyboardMarkup(List.of(firstRow, secondRow));
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

    /**
     * Отправить простое текстовое сообщение без клавиатуры.
     *
     * @param chatId идентификатор чата
     * @param text   текст сообщения
     */
    private void sendSimpleMessage(Long chatId, String text) {
        SendMessage msg = new SendMessage(chatId.toString(), text);
        try {
            telegramClient.execute(msg);
        } catch (TelegramApiException e) {
            log.error("❌ Ошибка отправки сообщения", e);
        }
    }

    /**
     * Попросить пользователя ввести своё ФИО.
     *
     * @param chatId идентификатор чата
     */
    private void promptForName(Long chatId) {
        awaitingName.put(chatId, Boolean.TRUE);
        sendSimpleMessage(chatId, "✍️ Пожалуйста, укажите своё ФИО");
    }

    /**
     * Отправить пользователю ФИО из системы для подтверждения.
     *
     * @param chatId   идентификатор чата
     * @param fullName имя, известное системе
     */
    private void sendNameConfirmation(Long chatId, String fullName) {
        KeyboardButton ok = new KeyboardButton("Верно");
        KeyboardButton wrong = new KeyboardButton("Неверно");
        KeyboardButton change = new KeyboardButton("Изменить");
        KeyboardRow first = new KeyboardRow(List.of(ok, wrong));
        KeyboardRow second = new KeyboardRow(List.of(change));
        ReplyKeyboardMarkup markup = new ReplyKeyboardMarkup(List.of(first, second));
        markup.setResizeKeyboard(true);
        markup.setOneTimeKeyboard(true);

        String text = String.format("У нас указано ваше ФИО: %s\nЭто верно?", fullName);
        SendMessage msg = new SendMessage(chatId.toString(), text);
        msg.setReplyMarkup(markup);
        try {
            telegramClient.execute(msg);
        } catch (TelegramApiException e) {
            log.error("❌ Ошибка отправки подтверждения имени", e);
        }
    }

    /**
     * Обработать контакт с номером телефона от пользователя.
     * <p>
     * Привязывает номер к покупателю, подтверждает Telegram и предлагает
     * подтвердить или указать ФИО.
     * </p>
     *
     * @param chatId  идентификатор чата Telegram
     * @param contact объект контакта с номером телефона
     */
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

            if (customer.getFullName() != null) {
                if (customer.getNameSource() != NameSource.USER_CONFIRMED) {
                    sendNameConfirmation(chatId, customer.getFullName());
                }
            } else {
                promptForName(chatId);
            }
        } catch (Exception e) {
            log.error("❌ Ошибка регистрации телефона {} для чата {}",
                    PhoneUtils.maskPhone(phone), chatId, e);
        }
    }
}