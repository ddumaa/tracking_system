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
        log.info("📩 Получено обновление: {}", formatUpdateMetadata(update));

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
                    refreshMainMenu(chatId);
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
                    sendMainMenu(chatId, customer.isNotificationsEnabled(),
                            customer.getNameSource() == NameSource.USER_CONFIRMED);
                    if (customer.getFullName() != null &&
                            customer.getNameSource() != NameSource.USER_CONFIRMED) {
                        sendNameConfirmation(chatId, customer.getFullName());
                    } else if (customer.getFullName() == null) {
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
                        refreshMainMenu(chatId);
                    }
                }
                else if ("🔔 Включить уведомления".equals(text)) {
                    boolean enabled = telegramService.enableNotifications(chatId);
                    if (enabled) {
                        refreshMainMenu(chatId);
                    }
                }
                else if ("✅ Подтвердить имя".equals(text)) {
                    if (telegramService.confirmName(chatId)) {
                        sendSimpleMessage(chatId, "✅ Спасибо, данные подтверждены");
                    }
                    refreshMainMenu(chatId);
                }
                else if ("✏️ Изменить имя".equals(text)) {
                    promptForName(chatId);
                }
                else if ("Верно".equalsIgnoreCase(text)) {
                    if (telegramService.confirmName(chatId)) {
                        sendSimpleMessage(chatId, "✅ Спасибо, данные подтверждены");
                    }
                    refreshMainMenu(chatId);
                }
                else if ("Неверно".equalsIgnoreCase(text)) {
                    telegramService.markNameUnconfirmed(chatId);
                    promptForName(chatId);
                    refreshMainMenu(chatId);
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
     * Формирует краткое текстовое описание обновления для безопасного логирования.
     *
     * @param update объект обновления Telegram
     * @return строка с типом события, идентификатором чата и маскированным телефоном (если есть)
     */
    private String formatUpdateMetadata(Update update) {
        if (update == null) {
            return "type=unknown";
        }

        String updateType = resolveUpdateType(update);
        Long chatId = resolveChatId(update);
        String maskedPhone = extractMaskedPhone(update);

        StringBuilder builder = new StringBuilder();
        builder.append("type=").append(updateType);
        builder.append(", chatId=").append(chatId != null ? chatId : "unknown");
        if (maskedPhone != null) {
            builder.append(", phone=").append(maskedPhone);
        }
        return builder.toString();
    }

    /**
     * Определяет тип обновления, ориентируясь на заполненные поля объекта {@link Update}.
     *
     * @param update объект обновления Telegram
     * @return строковое обозначение типа события
     */
    private String resolveUpdateType(Update update) {
        if (update.hasMessage()) {
            return "message";
        }
        if (update.hasEditedMessage()) {
            return "edited_message";
        }
        if (update.hasCallbackQuery()) {
            return "callback_query";
        }
        if (update.hasInlineQuery()) {
            return "inline_query";
        }
        if (update.hasChosenInlineQuery()) {
            return "chosen_inline_query";
        }
        if (update.hasChannelPost()) {
            return "channel_post";
        }
        if (update.hasEditedChannelPost()) {
            return "edited_channel_post";
        }
        if (update.hasShippingQuery()) {
            return "shipping_query";
        }
        if (update.hasPreCheckoutQuery()) {
            return "pre_checkout_query";
        }
        if (update.hasPoll()) {
            return "poll";
        }
        if (update.hasPollAnswer()) {
            return "poll_answer";
        }
        if (update.hasMyChatMember()) {
            return "my_chat_member";
        }
        if (update.hasChatMember()) {
            return "chat_member";
        }
        if (update.hasChatJoinRequest()) {
            return "chat_join_request";
        }
        return "unknown";
    }

    /**
     * Пытается извлечь идентификатор чата из обновления.
     *
     * @param update объект обновления Telegram
     * @return идентификатор чата или {@code null}, если определить не удалось
     */
    private Long resolveChatId(Update update) {
        if (update.hasMessage() && update.getMessage() != null) {
            return update.getMessage().getChatId();
        }
        if (update.hasEditedMessage() && update.getEditedMessage() != null) {
            return update.getEditedMessage().getChatId();
        }
        if (update.hasCallbackQuery()) {
            var callback = update.getCallbackQuery();
            if (callback != null && callback.getMessage() != null) {
                return callback.getMessage().getChatId();
            }
        }
        if (update.hasChannelPost() && update.getChannelPost() != null) {
            return update.getChannelPost().getChatId();
        }
        if (update.hasEditedChannelPost() && update.getEditedChannelPost() != null) {
            return update.getEditedChannelPost().getChatId();
        }
        if (update.hasMyChatMember()) {
            var myChatMember = update.getMyChatMember();
            if (myChatMember != null && myChatMember.getChat() != null) {
                return myChatMember.getChat().getId();
            }
        }
        if (update.hasChatMember()) {
            var chatMember = update.getChatMember();
            if (chatMember != null && chatMember.getChat() != null) {
                return chatMember.getChat().getId();
            }
        }
        if (update.hasChatJoinRequest()) {
            var joinRequest = update.getChatJoinRequest();
            if (joinRequest != null && joinRequest.getChat() != null) {
                return joinRequest.getChat().getId();
            }
        }
        return null;
    }

    /**
     * Находит телефон в обновлении и возвращает его маскированный вариант.
     *
     * @param update объект обновления Telegram
     * @return маскированный номер или {@code null}, если телефон отсутствует
     */
    private String extractMaskedPhone(Update update) {
        if (update.hasMessage() && update.getMessage() != null) {
            var message = update.getMessage();
            if (message.hasContact() && message.getContact() != null) {
                String phone = message.getContact().getPhoneNumber();
                if (phone != null && !phone.isBlank()) {
                    return PhoneUtils.maskPhone(phone);
                }
            }
        }
        return null;
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
     * Отправить главное меню с базовыми командами.
     * <p>Меню включает управление уведомлениями,
     * просмотр статистики и действия с ФИО.</p>
     *
     * @param chatId               идентификатор чата Telegram
     * @param notificationsEnabled текущий статус уведомлений
     * @param nameConfirmed        подтверждено ли имя пользователем
     */
    private void sendMainMenu(Long chatId, boolean notificationsEnabled, boolean nameConfirmed) {
        String notifyText = notificationsEnabled
                ? "🔕 Отключить уведомления" : "🔔 Включить уведомления";
        String nameText = nameConfirmed
                ? "✏️ Изменить имя" : "✅ Подтвердить имя";

        KeyboardButton notifyButton = new KeyboardButton(notifyText);
        KeyboardButton statsButton = new KeyboardButton("📊 Моя статистика");
        KeyboardButton nameButton = new KeyboardButton(nameText);

        KeyboardRow firstRow = new KeyboardRow(List.of(notifyButton));
        KeyboardRow secondRow = new KeyboardRow(List.of(statsButton));
        KeyboardRow thirdRow = new KeyboardRow(List.of(nameButton));
        ReplyKeyboardMarkup markup = new ReplyKeyboardMarkup(List.of(firstRow, secondRow, thirdRow));
        markup.setResizeKeyboard(true);
        markup.setOneTimeKeyboard(false);

        SendMessage message = new SendMessage(chatId.toString(), "📋 Главное меню");
        message.setReplyMarkup(markup);

        try {
            telegramClient.execute(message);
        } catch (TelegramApiException e) {
            log.error("❌ Ошибка отправки главного меню", e);
        }
    }

    /**
     * Обновить главное меню, учитывая текущее состояние покупателя.
     *
     * @param chatId идентификатор чата Telegram
     */
    private void refreshMainMenu(Long chatId) {
        telegramService.findByChatId(chatId).ifPresent(c ->
                sendMainMenu(chatId, c.isNotificationsEnabled(),
                        c.getNameSource() == NameSource.USER_CONFIRMED));
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
        // Клавиатура остаётся на экране, пока пользователь не выберет действие
        markup.setOneTimeKeyboard(false);

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
                sendMainMenu(chatId, true,
                        customer.getNameSource() == NameSource.USER_CONFIRMED);
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