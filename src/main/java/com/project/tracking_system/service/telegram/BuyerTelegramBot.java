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

    enum ChatState {
        IDLE,
        AWAITING_CONTACT,
        AWAITING_NAME_INPUT
    }

    private final TelegramClient telegramClient;
    private final CustomerTelegramService telegramService;
    private final String botToken;
    private final Map<Long, ChatState> chatStates = new ConcurrentHashMap<>();

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

        if (!update.hasMessage() || update.getMessage() == null) {
            return;
        }

        var message = update.getMessage();
        Long chatId = message.getChatId();

        if (message.hasText()) {
            handleTextMessage(chatId, message.getText());
        }

        if (message.hasContact()) {
            handleContact(chatId, message.getContact());
        }
    }

    /**
     * Обрабатывает текстовое сообщение пользователя с учётом текущего состояния диалога.
     *
     * @param chatId идентификатор чата Telegram
     * @param text   текст сообщения
     */
    private void handleTextMessage(Long chatId, String text) {
        if (chatId == null || text == null) {
            return;
        }

        String trimmed = text.trim();
        if (trimmed.isEmpty()) {
            return;
        }

        if ("/menu".equals(trimmed)) {
            handleMenuCommand(chatId);
            return;
        }

        if ("/start".equals(trimmed)) {
            handleStartCommand(chatId);
            return;
        }

        ChatState state = getState(chatId);

        if (state == ChatState.AWAITING_CONTACT) {
            if (trimmed.startsWith("/")) {
                remindContactRequired(chatId);
            } else {
                handleAwaitedPhoneText(chatId, trimmed);
            }
            return;
        }

        if (state == ChatState.AWAITING_NAME_INPUT) {
            if (trimmed.startsWith("/") || isNameControlCommand(trimmed)) {
                remindNameRequired(chatId);
            } else {
                handleNameInput(chatId, trimmed);
            }
            return;
        }

        handleIdleText(chatId, trimmed);
    }

    /**
     * Обрабатывает команду /start, инициируя ожидание контакта или показывая меню.
     *
     * @param chatId идентификатор чата Telegram
     */
    private void handleStartCommand(Long chatId) {
        log.info("✅ Команда /start получена от {}", chatId);
        Optional<Customer> optional = telegramService.findByChatId(chatId);
        if (optional.isEmpty()) {
            transitionToState(chatId, ChatState.AWAITING_CONTACT);
            sendSharePhoneKeyboard(chatId);
            return;
        }

        Customer customer = optional.get();
        transitionToState(chatId, ChatState.IDLE);
        sendMainMenu(chatId, customer.isNotificationsEnabled(),
                customer.getNameSource() == NameSource.USER_CONFIRMED);

        if (customer.getFullName() != null
                && customer.getNameSource() != NameSource.USER_CONFIRMED) {
            sendNameConfirmation(chatId, customer.getFullName());
        } else if (customer.getFullName() == null) {
            promptForName(chatId);
        }
    }

    /**
     * Показывает главное меню и возвращает сценарий в состояние IDLE.
     *
     * @param chatId идентификатор чата Telegram
     */
    private void handleMenuCommand(Long chatId) {
        transitionToState(chatId, ChatState.IDLE);
        Optional<Customer> optional = telegramService.findByChatId(chatId);
        if (optional.isPresent()) {
            Customer customer = optional.get();
            sendMainMenu(chatId, customer.isNotificationsEnabled(),
                    customer.getNameSource() == NameSource.USER_CONFIRMED);
            if (customer.getFullName() == null) {
                sendSimpleMessage(chatId,
                        "✍️ Чтобы указать ФИО, воспользуйтесь пунктом \"✏️ Изменить имя\" в меню.");
            } else if (customer.getNameSource() != NameSource.USER_CONFIRMED) {
                sendNameConfirmation(chatId, customer.getFullName());
            }
            return;
        }

        sendSimpleMessage(chatId,
                "📱 Чтобы пользоваться меню, сначала отправьте /start и поделитесь контактом.");
    }

    /**
     * Обрабатывает текстовые команды и нажатия кнопок в состоянии ожидания.
     *
     * @param chatId идентификатор чата Telegram
     * @param text   текст сообщения
     */
    private void handleIdleText(Long chatId, String text) {
        if ("/stop".equals(text) || "/unsubscribe".equals(text)) {
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
            return;
        }

        if ("🔕 Отключить уведомления".equals(text)) {
            boolean disabled = telegramService.disableNotifications(chatId);
            if (disabled) {
                refreshMainMenu(chatId);
            }
            return;
        }

        if ("🔔 Включить уведомления".equals(text)) {
            boolean enabled = telegramService.enableNotifications(chatId);
            if (enabled) {
                refreshMainMenu(chatId);
            }
            return;
        }

        if ("✅ Подтвердить имя".equals(text)) {
            if (telegramService.confirmName(chatId)) {
                sendSimpleMessage(chatId, "✅ Спасибо, данные подтверждены");
            }
            refreshMainMenu(chatId);
            return;
        }

        if ("✏️ Изменить имя".equals(text)) {
            promptForName(chatId);
            return;
        }

        if ("Верно".equalsIgnoreCase(text)) {
            if (telegramService.confirmName(chatId)) {
                sendSimpleMessage(chatId, "✅ Спасибо, данные подтверждены");
            }
            refreshMainMenu(chatId);
            return;
        }

        if ("Неверно".equalsIgnoreCase(text)) {
            telegramService.markNameUnconfirmed(chatId);
            promptForName(chatId);
            refreshMainMenu(chatId);
            return;
        }

        if ("Изменить".equalsIgnoreCase(text)) {
            promptForName(chatId);
            return;
        }

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

    /**
     * Сохраняет ФИО, введённое пользователем, и переводит диалог в состояние ожидания команд.
     *
     * @param chatId идентификатор чата Telegram
     * @param text   введённое пользователем ФИО
     */
    private void handleNameInput(Long chatId, String text) {
        String fullName = text.trim();
        if (fullName.isEmpty()) {
            remindNameRequired(chatId);
            return;
        }

        boolean saved = telegramService.updateNameFromTelegram(chatId, fullName);
        if (!saved) {
            sendSimpleMessage(chatId,
                    "⚠️ Не удалось сохранить ФИО. Попробуйте отправить его ещё раз или воспользуйтесь /menu.");
            return;
        }

        sendSimpleMessage(chatId, "✅ ФИО сохранено и подтверждено");
        transitionToState(chatId, ChatState.IDLE);
        refreshMainMenu(chatId);
    }

    /**
     * Сообщает пользователю, что требуется поделиться контактом.
     *
     * @param chatId идентификатор чата Telegram
     */
    private void remindContactRequired(Long chatId) {
        sendSimpleMessage(chatId,
                "📱 Пожалуйста, поделитесь контактом через кнопку или отправьте номер. Для возврата в меню используйте /menu.");
    }

    /**
     * Сообщает пользователю, что бот ожидает ввод ФИО.
     *
     * @param chatId идентификатор чата Telegram
     */
    private void remindNameRequired(Long chatId) {
        sendSimpleMessage(chatId,
                "✍️ Сейчас ожидается ввод ФИО. Отправьте своё имя сообщением или вернитесь в меню командой /menu.");
    }

    /**
     * Проверяет, относится ли текст к кнопкам управления именем.
     *
     * @param text текст сообщения пользователя
     * @return {@code true}, если сообщение соответствует управляющей фразе
     */
    private boolean isNameControlCommand(String text) {
        return "✅ Подтвердить имя".equals(text)
                || "✏️ Изменить имя".equals(text)
                || "Верно".equalsIgnoreCase(text)
                || "Неверно".equalsIgnoreCase(text)
                || "Изменить".equalsIgnoreCase(text);
    }

    /**
     * Фиксирует новое состояние сценария для указанного чата.
     *
     * @param chatId идентификатор чата Telegram
     * @param state  состояние, в которое нужно перевести сценарий
     */
    private void transitionToState(Long chatId, ChatState state) {
        if (chatId == null || state == null) {
            return;
        }

        if (state == ChatState.IDLE) {
            chatStates.remove(chatId);
        } else {
            chatStates.put(chatId, state);
        }
    }

    /**
     * Возвращает зафиксированное состояние чата.
     *
     * @param chatId идентификатор чата Telegram
     * @return сохранённое состояние или {@link ChatState#IDLE}, если чат не отслеживается
     */
    ChatState getState(Long chatId) {
        if (chatId == null) {
            return ChatState.IDLE;
        }
        return chatStates.getOrDefault(chatId, ChatState.IDLE);
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
        sendPhoneRequestMessage(chatId,
                "👋 Чтобы получать уведомления о посылках, поделитесь номером телефона.");
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
     * Обрабатывает текстовый ввод телефона, если бот ожидает номер.
     * <p>
     * При успешном распознавании отправляется маскированный номер и клавиатура
     * с запросом контакта. В случае ошибки пользователю показываются примеры
     * корректных форматов.
     * </p>
     *
     * @param chatId идентификатор чата Telegram
     * @param text   текст, введённый пользователем
     */
    private void handleAwaitedPhoneText(Long chatId, String text) {
        String candidate = text == null ? "" : text.trim();
        if (candidate.isEmpty()) {
            sendPhoneFormatHint(chatId);
            return;
        }

        try {
            String normalized = PhoneUtils.normalizePhone(candidate);
            String masked = PhoneUtils.maskPhone(normalized);
            sendPhoneRecognitionMessage(chatId, masked);
        } catch (IllegalArgumentException ex) {
            log.info("⚠️ Не удалось распознать номер для чата {}", chatId);
            sendPhoneFormatHint(chatId);
        }
    }

    /**
     * Создаёт клавиатуру с кнопкой запроса контакта.
     *
     * @return разметка клавиатуры Telegram
     */
    private ReplyKeyboardMarkup createPhoneRequestKeyboard() {
        KeyboardButton button = new KeyboardButton("📱 Поделиться номером");
        button.setRequestContact(true);
        KeyboardRow row = new KeyboardRow(List.of(button));
        ReplyKeyboardMarkup markup = new ReplyKeyboardMarkup(List.of(row));
        markup.setResizeKeyboard(true);
        markup.setOneTimeKeyboard(true);
        return markup;
    }

    /**
     * Отправляет сообщение с клавиатурой запроса телефона.
     *
     * @param chatId идентификатор чата
     * @param text   текст, который увидит пользователь
     */
    private void sendPhoneRequestMessage(Long chatId, String text) {
        SendMessage message = new SendMessage(chatId.toString(), text);
        message.setReplyMarkup(createPhoneRequestKeyboard());

        try {
            telegramClient.execute(message);
        } catch (TelegramApiException e) {
            log.error("❌ Ошибка отправки запроса номера", e);
        }
    }

    /**
     * Уведомляет пользователя о распознанном номере и просит подтвердить его.
     *
     * @param chatId      идентификатор чата Telegram
     * @param maskedPhone маскированный номер телефона
     */
    private void sendPhoneRecognitionMessage(Long chatId, String maskedPhone) {
        String text = String.format("Похоже, ваш номер: %s\n" +
                        "Пожалуйста, подтвердите его, поделившись контактом.",
                maskedPhone);
        sendPhoneRequestMessage(chatId, text);
    }

    /**
     * Показывает пользователю примеры корректного ввода номера телефона.
     *
     * @param chatId идентификатор чата Telegram
     */
    private void sendPhoneFormatHint(Long chatId) {
        String text = "Пока не удалось распознать номер. Примеры корректных форматов:\n" +
                "+375291234567\n" +
                "80291234567\n" +
                "8 029 123 45 67";
        sendPhoneRequestMessage(chatId, text);
    }

    /**
     * Попросить пользователя ввести своё ФИО.
     *
     * @param chatId идентификатор чата
     */
    private void promptForName(Long chatId) {
        transitionToState(chatId, ChatState.AWAITING_NAME_INPUT);
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
            }

            sendMainMenu(chatId, customer.isNotificationsEnabled(),
                    customer.getNameSource() == NameSource.USER_CONFIRMED);

            if (customer.getFullName() != null) {
                if (customer.getNameSource() != NameSource.USER_CONFIRMED) {
                    sendNameConfirmation(chatId, customer.getFullName());
                }
            } else {
                promptForName(chatId);
                return;
            }

            transitionToState(chatId, ChatState.IDLE);
        } catch (Exception e) {
            log.error("❌ Ошибка регистрации телефона {} для чата {}",
                    PhoneUtils.maskPhone(phone), chatId, e);
        }
    }
}