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
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageReplyMarkup;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Contact;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.util.ArrayList;
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

    private static final String BUTTON_STATS = "📊 Статистика";
    private static final String BUTTON_SETTINGS = "⚙️ Настройки";
    private static final String BUTTON_HELP = "❓ Помощь";
    private static final String BUTTON_BACK = "⬅️ Назад";

    private static final String CALLBACK_BACK_TO_MENU = "menu:back";
    private static final String CALLBACK_SETTINGS_TOGGLE_NOTIFICATIONS = "settings:toggle_notifications";
    private static final String CALLBACK_SETTINGS_CONFIRM_NAME = "settings:confirm_name";
    private static final String CALLBACK_SETTINGS_EDIT_NAME = "settings:edit_name";

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

        if (update.hasCallbackQuery()) {
            handleCallbackQuery(update.getCallbackQuery());
            return;
        }

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
     * Обрабатывает callback-запросы от инлайн-кнопок и выполняет выбранное действие.
     *
     * @param callbackQuery callback-запрос от Telegram
     */
    private void handleCallbackQuery(CallbackQuery callbackQuery) {
        if (callbackQuery == null) {
            return;
        }

        String data = callbackQuery.getData();
        Message message = callbackQuery.getMessage();
        Long chatId = message != null ? message.getChatId() : null;
        Integer messageId = message != null ? message.getMessageId() : null;

        if (data == null || chatId == null) {
            answerCallbackQuery(callbackQuery, "Команда недоступна");
            return;
        }

        switch (data) {
            case CALLBACK_SETTINGS_TOGGLE_NOTIFICATIONS ->
                    handleSettingsToggleNotifications(chatId, messageId, callbackQuery);
            case CALLBACK_SETTINGS_CONFIRM_NAME ->
                    handleSettingsConfirmName(chatId, messageId, callbackQuery);
            case CALLBACK_SETTINGS_EDIT_NAME ->
                    handleSettingsEditName(chatId, messageId, callbackQuery);
            case CALLBACK_BACK_TO_MENU ->
                    handleCallbackBackToMenu(chatId, messageId, callbackQuery);
            default -> answerCallbackQuery(callbackQuery, "Неизвестная команда");
        }
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
        sendMainMenu(chatId);

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
            sendMainMenu(chatId);
            if (customer.getFullName() == null || customer.getFullName().isBlank()) {
                sendSimpleMessage(chatId,
                        "✍️ Чтобы указать ФИО, откройте пункт \"⚙️ Настройки\" и выберите \"✍️ Указать имя\".");
            } else if (customer.getNameSource() != NameSource.USER_CONFIRMED) {
                sendSimpleMessage(chatId,
                        "ℹ️ Проверьте ФИО в разделе \"⚙️ Настройки\" и подтвердите его кнопкой \"✅ Подтвердить имя\".");
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

        if ("/stats".equals(text) || BUTTON_STATS.equals(text) || "📊 Моя статистика".equals(text)) {
            sendStatisticsScreen(chatId);
            return;
        }

        if (BUTTON_SETTINGS.equals(text)) {
            sendSettingsScreen(chatId);
            return;
        }

        if (BUTTON_HELP.equals(text) || "/help".equals(text)) {
            sendHelpScreen(chatId);
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
        }
    }

    /**
     * Отправляет статистику покупателя с кнопкой возврата к главному меню.
     *
     * @param chatId идентификатор чата Telegram
     */
    private void sendStatisticsScreen(Long chatId) {
        InlineKeyboardMarkup backMarkup = createBackInlineKeyboard();
        telegramService.getStatistics(chatId)
                .ifPresentOrElse(stats -> {
                    String stores = stats.getStoreNames().isEmpty()
                            ? "-" : String.join(", ", stats.getStoreNames());
                    String text = String.format(
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
                    sendInlineMessage(chatId, text, backMarkup);
                }, () -> sendInlineMessage(chatId,
                        "\uD83D\uDCCA Статистика пока недоступна. Попробуйте позже или проверьте, есть ли у вас активные заказы.",
                        backMarkup));
    }

    /**
     * Показывает экран настроек с инлайн-кнопками управления.
     *
     * @param chatId идентификатор чата Telegram
     */
    private void sendSettingsScreen(Long chatId) {
        Optional<Customer> optional = telegramService.findByChatId(chatId);
        if (optional.isEmpty()) {
            sendSimpleMessage(chatId,
                    "⚠️ Настройки недоступны. Отправьте /start и поделитесь контактом, чтобы продолжить.");
            return;
        }

        Customer customer = optional.get();
        boolean awaitingName = getState(chatId) == ChatState.AWAITING_NAME_INPUT;
        String text = buildSettingsText(customer, awaitingName);
        InlineKeyboardMarkup markup = buildSettingsKeyboard(customer);
        sendInlineMessage(chatId, text, markup);
    }

    /**
     * Отправляет справочную информацию по работе с ботом.
     *
     * @param chatId идентификатор чата Telegram
     */
    private void sendHelpScreen(Long chatId) {
        String helpText = """
                ❓ Помощь

                • /start — привязать чат и получать уведомления.
                • /menu — открыть главное меню.
                • /stats — показать статистику.

                Управляйте уведомлениями и ФИО через раздел "⚙️ Настройки".
                """;
        sendInlineMessage(chatId, helpText, createBackInlineKeyboard());
    }

    /**
     * Переключает состояние уведомлений при нажатии инлайн-кнопки.
     *
     * @param chatId        идентификатор чата Telegram
     * @param messageId     идентификатор сообщения с настройками
     * @param callbackQuery исходный callback-запрос
     */
    private void handleSettingsToggleNotifications(Long chatId, Integer messageId, CallbackQuery callbackQuery) {
        if (chatId == null) {
            answerCallbackQuery(callbackQuery, "Команда недоступна");
            return;
        }

        Optional<Customer> optional = telegramService.findByChatId(chatId);
        if (optional.isEmpty()) {
            answerCallbackQuery(callbackQuery, "Сначала привяжите чат через /start");
            sendSimpleMessage(chatId,
                    "⚠️ Настройки появятся после отправки номера телефона командой /start.");
            return;
        }

        Customer customer = optional.get();
        boolean notificationsEnabled = customer.isNotificationsEnabled();
        boolean changed = notificationsEnabled
                ? telegramService.disableNotifications(chatId)
                : telegramService.enableNotifications(chatId);
        if (changed) {
            customer.setNotificationsEnabled(!notificationsEnabled);
            answerCallbackQuery(callbackQuery, notificationsEnabled
                    ? "🔕 Уведомления отключены"
                    : "🔔 Уведомления включены");
        } else {
            answerCallbackQuery(callbackQuery, "Настройки не изменились");
        }

        updateSettingsMessage(chatId, messageId, customer);
    }

    /**
     * Подтверждает имя пользователя из раздела настроек.
     *
     * @param chatId        идентификатор чата Telegram
     * @param messageId     идентификатор сообщения с настройками
     * @param callbackQuery исходный callback-запрос
     */
    private void handleSettingsConfirmName(Long chatId, Integer messageId, CallbackQuery callbackQuery) {
        if (chatId == null) {
            answerCallbackQuery(callbackQuery, "Команда недоступна");
            return;
        }

        Optional<Customer> optional = telegramService.findByChatId(chatId);
        if (optional.isEmpty()) {
            answerCallbackQuery(callbackQuery, "Имя пока недоступно");
            sendSimpleMessage(chatId,
                    "⚠️ Сначала привяжите номер телефона командой /start, чтобы управлять именем.");
            return;
        }

        Customer customer = optional.get();
        String fullName = customer.getFullName();
        if (fullName == null || fullName.isBlank()) {
            answerCallbackQuery(callbackQuery, "Сначала укажите имя");
            return;
        }

        if (customer.getNameSource() == NameSource.USER_CONFIRMED) {
            answerCallbackQuery(callbackQuery, "Имя уже подтверждено");
            updateSettingsMessage(chatId, messageId, customer);
            return;
        }

        boolean confirmed = telegramService.confirmName(chatId);
        if (confirmed) {
            customer.setNameSource(NameSource.USER_CONFIRMED);
            sendSimpleMessage(chatId, "✅ Спасибо, данные подтверждены");
            answerCallbackQuery(callbackQuery, "Имя подтверждено");
        } else {
            answerCallbackQuery(callbackQuery, "Не удалось подтвердить имя");
        }

        updateSettingsMessage(chatId, messageId, customer);
    }

    /**
     * Переводит пользователя в режим ввода имени из раздела настроек.
     *
     * @param chatId        идентификатор чата Telegram
     * @param messageId     идентификатор сообщения с настройками
     * @param callbackQuery исходный callback-запрос
     */
    private void handleSettingsEditName(Long chatId, Integer messageId, CallbackQuery callbackQuery) {
        if (chatId == null) {
            answerCallbackQuery(callbackQuery, "Команда недоступна");
            return;
        }

        Optional<Customer> optional = telegramService.findByChatId(chatId);
        if (optional.isEmpty()) {
            answerCallbackQuery(callbackQuery, "Сначала привяжите чат");
            sendSimpleMessage(chatId,
                    "⚠️ Управление именем появится после привязки номера телефона через /start.");
            return;
        }

        Customer customer = optional.get();
        String prompt = (customer.getFullName() == null || customer.getFullName().isBlank())
                ? "✍️ Отправьте своё ФИО сообщением."
                : "✍️ Отправьте новое ФИО сообщением.";
        answerCallbackQuery(callbackQuery, "Ожидаю ввод ФИО");
        transitionToState(chatId, ChatState.AWAITING_NAME_INPUT);
        sendSimpleMessage(chatId, prompt);
        updateSettingsMessage(chatId, messageId, customer);
    }

    /**
     * Возвращает пользователя в главное меню из инлайн-режима.
     *
     * @param chatId        идентификатор чата Telegram
     * @param messageId     идентификатор исходного сообщения
     * @param callbackQuery исходный callback-запрос
     */
    private void handleCallbackBackToMenu(Long chatId, Integer messageId, CallbackQuery callbackQuery) {
        transitionToState(chatId, ChatState.IDLE);
        answerCallbackQuery(callbackQuery, "Главное меню");
        sendMainMenu(chatId);
        removeInlineKeyboard(chatId, messageId);
    }

    /**
     * Обновляет сообщение с настройками, подставляя актуальные данные.
     *
     * @param chatId   идентификатор чата Telegram
     * @param messageId идентификатор сообщения, которое требуется изменить
     * @param customer состояние покупателя для отображения
     */
    private void updateSettingsMessage(Long chatId, Integer messageId, Customer customer) {
        if (chatId == null || messageId == null || customer == null) {
            return;
        }

        EditMessageText edit = new EditMessageText();
        edit.setChatId(chatId.toString());
        edit.setMessageId(messageId);
        boolean awaitingName = getState(chatId) == ChatState.AWAITING_NAME_INPUT;
        edit.setText(buildSettingsText(customer, awaitingName));
        edit.setReplyMarkup(buildSettingsKeyboard(customer));
        try {
            telegramClient.execute(edit);
        } catch (TelegramApiException e) {
            log.error("❌ Ошибка обновления сообщения настроек", e);
        }
    }

    /**
     * Формирует текстовое описание текущих настроек покупателя.
     *
     * @param customer         сущность покупателя
     * @param awaitingNameInput ожидается ли ввод ФИО
     * @return текст для отображения в сообщении
     */
    private String buildSettingsText(Customer customer, boolean awaitingNameInput) {
        String notificationsStatus = customer.isNotificationsEnabled()
                ? "включены"
                : "отключены";

        String nameStatus;
        String fullName = customer.getFullName();
        if (fullName == null || fullName.isBlank()) {
            nameStatus = "не указано";
        } else if (customer.getNameSource() == NameSource.USER_CONFIRMED) {
            nameStatus = String.format("%s (подтверждено)", fullName);
        } else {
            nameStatus = String.format("%s (ожидает подтверждения)", fullName);
        }

        StringBuilder builder = new StringBuilder();
        builder.append("⚙️ Настройки\n\n");
        builder.append("Уведомления: ").append(notificationsStatus).append('\n');
        builder.append("Имя: ").append(nameStatus);
        if (awaitingNameInput) {
            builder.append("\n\n✍️ Ожидается ввод нового ФИО.");
        }
        return builder.toString();
    }

    /**
     * Создаёт инлайн-клавиатуру для раздела настроек.
     *
     * @param customer покупатель, для которого формируются кнопки
     * @return готовая инлайн-клавиатура
     */
    private InlineKeyboardMarkup buildSettingsKeyboard(Customer customer) {
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        InlineKeyboardButton notifyButton = new InlineKeyboardButton();
        notifyButton.setText(customer.isNotificationsEnabled()
                ? "🔕 Отключить уведомления"
                : "🔔 Включить уведомления");
        notifyButton.setCallbackData(CALLBACK_SETTINGS_TOGGLE_NOTIFICATIONS);
        rows.add(List.of(notifyButton));

        String fullName = customer.getFullName();
        boolean hasName = fullName != null && !fullName.isBlank();
        if (!hasName) {
            InlineKeyboardButton setNameButton = new InlineKeyboardButton();
            setNameButton.setText("✍️ Указать имя");
            setNameButton.setCallbackData(CALLBACK_SETTINGS_EDIT_NAME);
            rows.add(List.of(setNameButton));
        } else if (customer.getNameSource() == NameSource.USER_CONFIRMED) {
            InlineKeyboardButton editNameButton = new InlineKeyboardButton();
            editNameButton.setText("✏️ Изменить имя");
            editNameButton.setCallbackData(CALLBACK_SETTINGS_EDIT_NAME);
            rows.add(List.of(editNameButton));
        } else {
            InlineKeyboardButton confirmButton = new InlineKeyboardButton();
            confirmButton.setText("✅ Подтвердить имя");
            confirmButton.setCallbackData(CALLBACK_SETTINGS_CONFIRM_NAME);

            InlineKeyboardButton editNameButton = new InlineKeyboardButton();
            editNameButton.setText("✏️ Изменить имя");
            editNameButton.setCallbackData(CALLBACK_SETTINGS_EDIT_NAME);
            rows.add(List.of(confirmButton, editNameButton));
        }

        InlineKeyboardButton backButton = new InlineKeyboardButton();
        backButton.setText(BUTTON_BACK);
        backButton.setCallbackData(CALLBACK_BACK_TO_MENU);
        rows.add(List.of(backButton));

        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        markup.setKeyboard(rows);
        return markup;
    }

    /**
     * Создаёт инлайн-клавиатуру только с кнопкой возврата.
     *
     * @return клавиатура с кнопкой «Назад»
     */
    private InlineKeyboardMarkup createBackInlineKeyboard() {
        InlineKeyboardButton backButton = new InlineKeyboardButton();
        backButton.setText(BUTTON_BACK);
        backButton.setCallbackData(CALLBACK_BACK_TO_MENU);

        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        markup.setKeyboard(List.of(List.of(backButton)));
        return markup;
    }

    /**
     * Удаляет инлайн-клавиатуру из сообщения, чтобы предотвратить повторные нажатия.
     *
     * @param chatId    идентификатор чата Telegram
     * @param messageId идентификатор сообщения
     */
    private void removeInlineKeyboard(Long chatId, Integer messageId) {
        if (chatId == null || messageId == null) {
            return;
        }

        EditMessageReplyMarkup editMarkup = new EditMessageReplyMarkup();
        editMarkup.setChatId(chatId.toString());
        editMarkup.setMessageId(messageId);
        editMarkup.setReplyMarkup(null);
        try {
            telegramClient.execute(editMarkup);
        } catch (TelegramApiException e) {
            log.error("❌ Ошибка удаления инлайн-клавиатуры", e);
        }
    }

    /**
     * Отвечает на callback-запрос, завершая анимацию ожидания у пользователя.
     *
     * @param callbackQuery callback-запрос Telegram
     * @param text          текст подтверждения (может быть пустым)
     */
    private void answerCallbackQuery(CallbackQuery callbackQuery, String text) {
        if (callbackQuery == null) {
            return;
        }

        AnswerCallbackQuery answer = new AnswerCallbackQuery();
        answer.setCallbackQueryId(callbackQuery.getId());
        if (text != null && !text.isBlank()) {
            answer.setText(text);
        }
        try {
            telegramClient.execute(answer);
        } catch (TelegramApiException e) {
            log.error("❌ Ошибка ответа на callback", e);
        }
    }

    /**
     * Отправляет сообщение с инлайн-клавиатурой.
     *
     * @param chatId идентификатор чата Telegram
     * @param text   текст сообщения
     * @param markup инлайн-клавиатура, которую необходимо показать
     */
    private void sendInlineMessage(Long chatId, String text, InlineKeyboardMarkup markup) {
        SendMessage message = new SendMessage(chatId.toString(), text);
        message.setReplyMarkup(markup);
        try {
            telegramClient.execute(message);
        } catch (TelegramApiException e) {
            log.error("❌ Ошибка отправки сообщения с инлайн-кнопками", e);
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
     * Отправляет главное меню с основными разделами бота.
     * <p>Меню содержит кнопки статистики, настроек и помощи.</p>
     *
     * @param chatId идентификатор чата Telegram
     */
    private void sendMainMenu(Long chatId) {
        KeyboardButton statsButton = new KeyboardButton(BUTTON_STATS);
        KeyboardButton settingsButton = new KeyboardButton(BUTTON_SETTINGS);
        KeyboardButton helpButton = new KeyboardButton(BUTTON_HELP);

        KeyboardRow firstRow = new KeyboardRow(List.of(statsButton, settingsButton));
        KeyboardRow secondRow = new KeyboardRow(List.of(helpButton));
        ReplyKeyboardMarkup markup = new ReplyKeyboardMarkup(List.of(firstRow, secondRow));
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
     * Переотправляет главное меню, чтобы обновить клавиатуру у пользователя.
     *
     * @param chatId идентификатор чата Telegram
     */
    private void refreshMainMenu(Long chatId) {
        sendMainMenu(chatId);
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

            sendMainMenu(chatId);

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