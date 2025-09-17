package com.project.tracking_system.service.telegram;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.tracking_system.entity.BuyerBotScreen;
import com.project.tracking_system.entity.BuyerChatState;
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
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.api.objects.message.MaybeInaccessibleMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.chatmember.ChatMemberUpdated;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardRemove;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Telegram-бот для покупателей.
 */
@Component
@Slf4j
public class BuyerTelegramBot implements SpringLongPollingBot, LongPollingSingleThreadUpdateConsumer {

    private static final String BUTTON_STATS = "📊 Статистика";
    private static final String BUTTON_SETTINGS = "⚙️ Настройки";
    private static final String BUTTON_HELP = "❓ Помощь";
    private static final String BUTTON_MENU = "🏠 Меню";
    private static final String BUTTON_BACK = "⬅️ Назад";

    private static final String CALLBACK_BACK_TO_MENU = "menu:back";
    private static final String CALLBACK_MENU_SHOW_STATS = "menu:stats";
    private static final String CALLBACK_MENU_SHOW_SETTINGS = "menu:settings";
    private static final String CALLBACK_MENU_SHOW_HELP = "menu:help";
    private static final String CALLBACK_SETTINGS_TOGGLE_NOTIFICATIONS = "settings:toggle_notifications";
    private static final String CALLBACK_SETTINGS_CONFIRM_NAME = "settings:confirm_name";
    private static final String CALLBACK_SETTINGS_EDIT_NAME = "settings:edit_name";
    private static final String CALLBACK_NAME_CONFIRM = "name:confirm";
    private static final String CALLBACK_NAME_EDIT = "name:edit";

    private static final String NAME_CONFIRMATION_MISSING_MESSAGE =
            "⚠️ Пока в системе нет ФИО для подтверждения. Пожалуйста, укажите его полностью.";
    private static final String NAME_EDIT_ANCHOR_TEXT =
            "✍️ Отправьте новое ФИО сообщением.\n\nПосле ввода воспользуйтесь кнопкой «🏠 Меню», чтобы вернуться.";

    private final TelegramClient telegramClient;
    private final CustomerTelegramService telegramService;
    private final FullNameValidator fullNameValidator;
    private final ChatSessionRepository chatSessionRepository;
    private final ObjectMapper objectMapper;
    private final String botToken;

    /**
     * Создаёт телеграм-бота для покупателей.
     *
     * @param telegramClient       клиент Telegram, предоставляемый Spring
     * @param token                токен бота (может отсутствовать)
     * @param telegramService      сервис привязки покупателей к Telegram
     * @param fullNameValidator    валидатор для проверки корректности ФИО
     * @param chatSessionRepository репозиторий состояния чатов покупателей
     * @param objectMapper         преобразователь объектов Telegram в JSON-структуры
     */
    public BuyerTelegramBot(TelegramClient telegramClient,
                            @Value("${telegram.bot.token:}") String token,
                            CustomerTelegramService telegramService,
                            FullNameValidator fullNameValidator,
                            ChatSessionRepository chatSessionRepository,
                            ObjectMapper objectMapper) {
        this.telegramClient = telegramClient;
        this.botToken = token;
        this.telegramService = telegramService;
        this.fullNameValidator = fullNameValidator;
        this.chatSessionRepository = chatSessionRepository;
        this.objectMapper = objectMapper;
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
     * Возвращает обработчик обновлений, регистрируя самого бота как потребителя.
     *
     * @return обработчик обновлений Telegram
     */
    @Override
    public LongPollingUpdateConsumer getUpdatesConsumer() {
        return this;
    }

    /**
     * Обрабатывает входящее обновление Telegram, реагируя на сообщения и callback-запросы.
     *
     * @param update объект обновления Telegram
     */
    @Override
    public void consume(Update update) {
        log.info("📩 Получено обновление: {}", formatUpdateMetadata(update));

        if (update.hasCallbackQuery()) {
            handleCallbackQuery(update.getCallbackQuery());
            return;
        }

        if (update.hasMyChatMember()) {
            handleMyChatMember(update.getMyChatMember());
            return;
        }

        if (!update.hasMessage() || update.getMessage() == null) {
            return;
        }

        var message = update.getMessage();
        Long chatId = message.getChatId();

        boolean keyboardRemoved = detectPersistentKeyboardRemoval(chatId, message);
        restorePersistentKeyboardIfNeeded(chatId, keyboardRemoved);

        if (message.hasText()) {
            handleTextMessage(chatId, message.getText());
        }

        if (message.hasContact()) {
            handleContact(chatId, message, message.getContact());
        }
    }

    /**
     * Обрабатывает обновление статуса чата бота и переотправляет нужный экран.
     * <p>
     * Если покупатель уже привязан к Telegram, бот возвращается в состояние ожидания команд
     * и заново отправляет главное меню вместе с постоянной клавиатурой. Для непривязанных
     * пользователей повторно запрашивается контакт с кнопкой «📱 Поделиться номером».
     * </p>
     *
     * @param myChatMember данные обновления chat_member от Telegram
     */
    private void handleMyChatMember(ChatMemberUpdated myChatMember) {
        if (myChatMember == null || myChatMember.getChat() == null) {
            return;
        }

        Long chatId = myChatMember.getChat().getId();
        if (chatId == null) {
            return;
        }

        Optional<Customer> optional = telegramService.findByChatId(chatId);
        if (optional.isPresent()) {
            transitionToState(chatId, BuyerChatState.IDLE);
            chatSessionRepository.markKeyboardHidden(chatId);
            sendMainMenu(chatId);
            return;
        }

        transitionToState(chatId, BuyerChatState.AWAITING_CONTACT);
        sendSharePhoneKeyboard(chatId);
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
        BuyerChatState state = getState(chatId);

        if (state == BuyerChatState.AWAITING_CONTACT) {
            if (trimmed.isEmpty() || trimmed.startsWith("/")) {
                remindContactRequired(chatId);
                return;
            }

            handleAwaitedPhoneText(chatId, trimmed);
            return;
        }

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

        if (state == BuyerChatState.AWAITING_NAME_INPUT) {
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
        MaybeInaccessibleMessage callbackMessage = callbackQuery.getMessage();
        Long chatId = callbackMessage != null ? callbackMessage.getChatId() : null;
        Integer messageId = callbackMessage != null ? callbackMessage.getMessageId() : null;

        if (data == null || chatId == null) {
            answerCallbackQuery(callbackQuery, "Команда недоступна");
            return;
        }

        ChatSession session = chatSessionRepository.find(chatId).orElse(null);
        if (isCallbackFromOutdatedMessage(messageId, session)) {
            handleOutdatedCallback(chatId, messageId, callbackQuery, session);
            return;
        }

        rememberAnchorMessage(chatId, messageId);

        switch (data) {
            case CALLBACK_MENU_SHOW_STATS -> handleMenuOpenStats(chatId, callbackQuery);
            case CALLBACK_MENU_SHOW_SETTINGS -> handleMenuOpenSettings(chatId, callbackQuery);
            case CALLBACK_MENU_SHOW_HELP -> handleMenuOpenHelp(chatId, callbackQuery);
            case CALLBACK_SETTINGS_TOGGLE_NOTIFICATIONS ->
                    handleSettingsToggleNotifications(chatId, callbackQuery);
            case CALLBACK_SETTINGS_CONFIRM_NAME ->
                    handleSettingsConfirmName(chatId, callbackQuery);
            case CALLBACK_SETTINGS_EDIT_NAME ->
                    handleSettingsEditName(chatId, callbackQuery);
            case CALLBACK_NAME_CONFIRM -> handleNameConfirmationCallback(chatId, callbackQuery);
            case CALLBACK_NAME_EDIT -> handleNameEditCallback(chatId, callbackQuery);
            case CALLBACK_BACK_TO_MENU ->
                    handleCallbackBackToMenu(chatId, callbackQuery);
            default -> answerCallbackQuery(callbackQuery, "Неизвестная команда");
        }
    }

    /**
     * Запоминает идентификатор якорного сообщения для дальнейшего редактирования.
     *
     * @param chatId    идентификатор чата Telegram
     * @param messageId идентификатор сообщения, отправленного ботом
     */
    private void rememberAnchorMessage(Long chatId, Integer messageId) {
        if (chatId == null || messageId == null) {
            return;
        }
        chatSessionRepository.updateAnchor(chatId, messageId);
    }

    /**
     * Проверяет, относится ли callback к устаревшему сообщению, отличному от текущего якоря.
     *
     * @param messageId идентификатор сообщения, из которого пришёл callback
     * @param state     сохранённое состояние чата
     * @return {@code true}, если callback относится к устаревшему сообщению
     */
    private boolean isCallbackFromOutdatedMessage(Integer messageId, ChatSession session) {
        return session != null
                && session.getAnchorMessageId() != null
                && messageId != null
                && !session.getAnchorMessageId().equals(messageId);
    }

    /**
     * Обрабатывает нажатия на устаревшие сообщения: уведомляет пользователя и перерисовывает актуальный экран.
     *
     * @param chatId        идентификатор чата Telegram
     * @param messageId     идентификатор устаревшего сообщения
     * @param callbackQuery исходный callback-запрос
     * @param state         сохранённое состояние чата с данными о последнем экране
     */
    private void handleOutdatedCallback(Long chatId,
                                        Integer messageId,
                                        CallbackQuery callbackQuery,
                                        ChatSession session) {
        answerCallbackQuery(callbackQuery, "Экран обновлён");
        removeInlineKeyboard(chatId, messageId);
        BuyerBotScreen screen = session != null ? session.getLastScreen() : null;
        renderScreen(chatId, screen);
    }

    /**
     * Удаляет инлайн-клавиатуру со старого сообщения, чтобы предотвратить повторные клики.
     *
     * @param chatId    идентификатор чата Telegram
     * @param messageId идентификатор устаревшего сообщения
     */
    private void removeInlineKeyboard(Long chatId, Integer messageId) {
        if (chatId == null || messageId == null) {
            return;
        }

        EditMessageReplyMarkup editMarkup = EditMessageReplyMarkup.builder()
                .chatId(chatId.toString())
                .messageId(messageId)
                .replyMarkup(null)
                .build();
        try {
            telegramClient.execute(editMarkup);
        } catch (TelegramApiException e) {
            log.debug("ℹ️ Не удалось снять клавиатуру с сообщения {} в чате {}", messageId, chatId, e);
        }
    }

    /**
     * Перерисовывает актуальный экран в якорном сообщении.
     *
     * @param chatId идентификатор чата Telegram
     * @param screen последний сохранённый экран
     */
    private void renderScreen(Long chatId, BuyerBotScreen screen) {
        if (chatId == null) {
            return;
        }

        if (screen == null) {
            sendMainMenu(chatId);
            return;
        }

        switch (screen) {
            case MENU -> sendMainMenu(chatId);
            case STATISTICS -> sendStatisticsScreen(chatId);
            case SETTINGS -> sendSettingsScreen(chatId);
            case HELP -> sendHelpScreen(chatId);
            case NAME_CONFIRMATION -> renderNameConfirmationScreen(chatId);
            case NAME_EDIT_PROMPT -> sendNameEditPromptScreen(chatId);
            default -> sendMainMenu(chatId);
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
            transitionToState(chatId, BuyerChatState.AWAITING_CONTACT);
            sendSharePhoneKeyboard(chatId);
            return;
        }

        Customer customer = optional.get();
        BuyerChatState previousState = getState(chatId);
        transitionToState(chatId, BuyerChatState.IDLE);
        if (previousState != BuyerChatState.AWAITING_CONTACT) {
            chatSessionRepository.markKeyboardHidden(chatId);
        }
        sendMainMenu(chatId);

        if (!ensureValidStoredNameOrRequestUpdate(chatId, customer)) {
            return;
        }

        String fullName = customer.getFullName();
        if (fullName != null
                && !fullName.isBlank()
                && customer.getNameSource() != NameSource.USER_CONFIRMED) {
            sendNameConfirmation(chatId, fullName);
        } else if (fullName == null) {
            promptForName(chatId);
        }
    }

    /**
     * Показывает экран статистики при выборе пункта главного меню.
     *
     * @param chatId        идентификатор чата Telegram
     * @param callbackQuery исходный callback-запрос
     */
    private void handleMenuOpenStats(Long chatId, CallbackQuery callbackQuery) {
        if (chatId == null) {
            answerCallbackQuery(callbackQuery, "Команда недоступна");
            return;
        }
        answerCallbackQuery(callbackQuery, "Статистика");
        sendStatisticsScreen(chatId);
    }

    /**
     * Показывает экран настроек из главного меню.
     *
     * @param chatId        идентификатор чата Telegram
     * @param callbackQuery исходный callback-запрос
     */
    private void handleMenuOpenSettings(Long chatId, CallbackQuery callbackQuery) {
        if (chatId == null) {
            answerCallbackQuery(callbackQuery, "Команда недоступна");
            return;
        }
        answerCallbackQuery(callbackQuery, "Настройки");
        sendSettingsScreen(chatId);
    }

    /**
     * Показывает раздел помощи по нажатию кнопки главного меню.
     *
     * @param chatId        идентификатор чата Telegram
     * @param callbackQuery исходный callback-запрос
     */
    private void handleMenuOpenHelp(Long chatId, CallbackQuery callbackQuery) {
        if (chatId == null) {
            answerCallbackQuery(callbackQuery, "Команда недоступна");
            return;
        }
        answerCallbackQuery(callbackQuery, "Помощь");
        sendHelpScreen(chatId);
    }

    /**
     * Подтверждает имя из якорного сообщения главного меню.
     *
     * @param chatId        идентификатор чата Telegram
     * @param callbackQuery исходный callback-запрос
     */
    private void handleNameConfirmationCallback(Long chatId, CallbackQuery callbackQuery) {
        if (chatId == null) {
            answerCallbackQuery(callbackQuery, "Команда недоступна");
            return;
        }

        boolean confirmed = confirmNameAndNotify(chatId);
        if (confirmed) {
            answerCallbackQuery(callbackQuery, "Имя подтверждено");
            sendMainMenu(chatId);
        } else {
            answerCallbackQuery(callbackQuery, "Не удалось подтвердить имя");
            sendNameConfirmationFailure(chatId);
            sendMainMenu(chatId);
        }
    }

    /**
     * Переводит пользователя в режим ввода нового имени из якорного сообщения.
     *
     * @param chatId        идентификатор чата Telegram
     * @param callbackQuery исходный callback-запрос
     */
    private void handleNameEditCallback(Long chatId, CallbackQuery callbackQuery) {
        if (chatId == null) {
            answerCallbackQuery(callbackQuery, "Команда недоступна");
            return;
        }

        answerCallbackQuery(callbackQuery, "Ожидаю ввод ФИО");
        telegramService.markNameUnconfirmed(chatId);
        transitionToState(chatId, BuyerChatState.AWAITING_NAME_INPUT);

        String prompt = "✍️ Отправьте новое ФИО сообщением.";
        sendSimpleMessage(chatId, prompt);
        sendNameEditPromptScreen(chatId);
    }

    /**
     * Показывает инструкцию по вводу нового ФИО в якорном сообщении.
     *
     * @param chatId идентификатор чата Telegram
     */
    private void sendNameEditPromptScreen(Long chatId) {
        sendInlineMessage(chatId, NAME_EDIT_ANCHOR_TEXT, createBackInlineKeyboard(), BuyerBotScreen.NAME_EDIT_PROMPT);
    }

    /**
     * Показывает главное меню и возвращает сценарий в состояние IDLE.
     *
     * @param chatId идентификатор чата Telegram
     */
    private void handleMenuCommand(Long chatId) {
        transitionToState(chatId, BuyerChatState.IDLE);
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

        if (BUTTON_MENU.equals(text)) {
            sendMainMenu(chatId);
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

        if (fullNameValidator.isConfirmationPhrase(text)) {
            boolean confirmed = confirmNameAndNotify(chatId);
            if (confirmed) {
                refreshMainMenu(chatId);
            } else {
                sendNameConfirmationFailure(chatId);
            }
            return;
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
                    sendInlineMessage(chatId, text, backMarkup, BuyerBotScreen.STATISTICS);
                }, () -> sendInlineMessage(chatId,
                        "\uD83D\uDCCA Статистика пока недоступна. Попробуйте позже или проверьте, есть ли у вас активные заказы.",
                        backMarkup,
                        BuyerBotScreen.STATISTICS));
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
        if (!ensureValidStoredNameOrRequestUpdate(chatId, customer)) {
            return;
        }

        boolean awaitingName = getState(chatId) == BuyerChatState.AWAITING_NAME_INPUT;
        String text = buildSettingsText(customer, awaitingName);
        InlineKeyboardMarkup markup = buildSettingsKeyboard(customer);
        sendInlineMessage(chatId, text, markup, BuyerBotScreen.SETTINGS);
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
        sendInlineMessage(chatId, helpText, createBackInlineKeyboard(), BuyerBotScreen.HELP);
    }

    /**
     * Переключает состояние уведомлений при нажатии инлайн-кнопки.
     *
     * @param chatId        идентификатор чата Telegram
     * @param callbackQuery исходный callback-запрос
     */
    private void handleSettingsToggleNotifications(Long chatId, CallbackQuery callbackQuery) {
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

        renderSettingsScreen(chatId, customer);
    }

    /**
     * Подтверждает имя пользователя из раздела настроек.
     *
     * @param chatId        идентификатор чата Telegram
     * @param callbackQuery исходный callback-запрос
     */
    private void handleSettingsConfirmName(Long chatId, CallbackQuery callbackQuery) {
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
            renderSettingsScreen(chatId, customer);
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

        renderSettingsScreen(chatId, customer);
    }

    /**
     * Переводит пользователя в режим ввода имени из раздела настроек.
     *
     * @param chatId        идентификатор чата Telegram
     * @param callbackQuery исходный callback-запрос
     */
    private void handleSettingsEditName(Long chatId, CallbackQuery callbackQuery) {
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
        transitionToState(chatId, BuyerChatState.AWAITING_NAME_INPUT);
        sendSimpleMessage(chatId, prompt);
        renderSettingsScreen(chatId, customer);
    }

    /**
     * Возвращает пользователя в главное меню из инлайн-режима.
     *
     * @param chatId        идентификатор чата Telegram
     * @param callbackQuery исходный callback-запрос
     */
    private void handleCallbackBackToMenu(Long chatId, CallbackQuery callbackQuery) {
        transitionToState(chatId, BuyerChatState.IDLE);
        answerCallbackQuery(callbackQuery, "Открыл меню");
        sendMainMenu(chatId);
    }

    /**
     * Обновляет сообщение с настройками, подставляя актуальные данные.
     *
     * @param chatId   идентификатор чата Telegram
     * @param messageId идентификатор сообщения, которое требуется изменить
     * @param customer состояние покупателя для отображения
     */
    private void renderSettingsScreen(Long chatId, Customer customer) {
        if (chatId == null || customer == null) {
            return;
        }

        boolean awaitingName = getState(chatId) == BuyerChatState.AWAITING_NAME_INPUT;
        String settingsText = buildSettingsText(customer, awaitingName);
        InlineKeyboardMarkup settingsKeyboard = buildSettingsKeyboard(customer);
        sendInlineMessage(chatId, settingsText, settingsKeyboard, BuyerBotScreen.SETTINGS);
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
        List<InlineKeyboardRow> rows = new ArrayList<>();

        InlineKeyboardButton notifyButton = InlineKeyboardButton.builder()
                .text(customer.isNotificationsEnabled()
                        ? "🔕 Отключить уведомления"
                        : "🔔 Включить уведомления")
                .callbackData(CALLBACK_SETTINGS_TOGGLE_NOTIFICATIONS)
                .build();
        rows.add(new InlineKeyboardRow(notifyButton));

        String fullName = customer.getFullName();
        boolean hasName = fullName != null && !fullName.isBlank();
        if (!hasName) {
            InlineKeyboardButton setNameButton = InlineKeyboardButton.builder()
                    .text("✍️ Указать имя")
                    .callbackData(CALLBACK_SETTINGS_EDIT_NAME)
                    .build();
            rows.add(new InlineKeyboardRow(setNameButton));
        } else if (customer.getNameSource() == NameSource.USER_CONFIRMED) {
            InlineKeyboardButton editNameButton = InlineKeyboardButton.builder()
                    .text("✏️ Изменить имя")
                    .callbackData(CALLBACK_SETTINGS_EDIT_NAME)
                    .build();
            rows.add(new InlineKeyboardRow(editNameButton));
        } else {
            InlineKeyboardButton confirmButton = InlineKeyboardButton.builder()
                    .text("✅ Подтвердить имя")
                    .callbackData(CALLBACK_SETTINGS_CONFIRM_NAME)
                    .build();

            InlineKeyboardButton editNameButton = InlineKeyboardButton.builder()
                    .text("✏️ Изменить имя")
                    .callbackData(CALLBACK_SETTINGS_EDIT_NAME)
                    .build();
            rows.add(new InlineKeyboardRow(confirmButton, editNameButton));
        }

        InlineKeyboardButton backButton = InlineKeyboardButton.builder()
                .text(BUTTON_BACK)
                .callbackData(CALLBACK_BACK_TO_MENU)
                .build();
        rows.add(new InlineKeyboardRow(backButton));

        return InlineKeyboardMarkup.builder()
                .keyboard(rows)
                .build();
    }

    /**
     * Создаёт инлайн-клавиатуру только с кнопкой возврата.
     *
     * @return клавиатура с кнопкой «Назад»
     */
    /**
     * Создаёт кнопку возврата к главному меню.
     *
     * @return инлайн-кнопка «Назад»
     */
    private InlineKeyboardButton buildBackButton() {
        return InlineKeyboardButton.builder()
                .text(BUTTON_BACK)
                .callbackData(CALLBACK_BACK_TO_MENU)
                .build();
    }

    /**
     * Создаёт инлайн-клавиатуру только с кнопкой возврата.
     *
     * @return клавиатура с кнопкой «Назад»
     */
    private InlineKeyboardMarkup createBackInlineKeyboard() {
        return InlineKeyboardMarkup.builder()
                .keyboard(List.of(new InlineKeyboardRow(buildBackButton())))
                .build();
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

        AnswerCallbackQuery.AnswerCallbackQueryBuilder builder = AnswerCallbackQuery.builder()
                .callbackQueryId(callbackQuery.getId());
        if (text != null && !text.isBlank()) {
            builder.text(text);
        }
        AnswerCallbackQuery answer = builder.build();
        try {
            telegramClient.execute(answer);
        } catch (TelegramApiException e) {
            log.error("❌ Ошибка ответа на callback", e);
        }
    }

    /**
     * Валидирует и сохраняет ФИО, введённое пользователем, переводя сценарий в режим команд.
     *
     * @param chatId идентификатор чата Telegram
     * @param text   введённое пользователем ФИО
     */
    private void handleNameInput(Long chatId, String text) {
        String candidate = text == null ? "" : text.trim();
        if (candidate.isEmpty()) {
            remindNameRequired(chatId);
            return;
        }

        FullNameValidator.FullNameValidationResult validation = fullNameValidator.validate(candidate);

        if (validation.error() == FullNameValidator.FullNameValidationError.CONFIRMATION_PHRASE) {
            boolean confirmed = confirmNameAndNotify(chatId);
            if (confirmed) {
                transitionToState(chatId, BuyerChatState.IDLE);
                refreshMainMenu(chatId);
            } else {
                sendNameConfirmationFailure(chatId);
            }
            return;
        }

        if (!validation.valid()) {
            sendSimpleMessage(chatId, validation.message());
            return;
        }

        boolean saved = telegramService.updateNameFromTelegram(chatId, validation.normalizedFullName());
        if (!saved) {
            sendSimpleMessage(chatId,
                    "⚠️ Не удалось сохранить ФИО. Попробуйте отправить его ещё раз или воспользуйтесь /menu.");
            return;
        }

        sendSimpleMessage(chatId, "✅ ФИО сохранено и подтверждено");
        transitionToState(chatId, BuyerChatState.IDLE);
        refreshMainMenu(chatId);
    }

    /**
     * Подтверждает ФИО в профиле покупателя и уведомляет о результате.
     *
     * @param chatId идентификатор чата Telegram
     * @return {@code true}, если подтверждение прошло успешно
     */
    private boolean confirmNameAndNotify(Long chatId) {
        boolean confirmed = telegramService.confirmName(chatId);
        if (confirmed) {
            sendSimpleMessage(chatId, "✅ Спасибо, данные подтверждены");
        }
        return confirmed;
    }

    /**
     * Сообщает пользователю, что подтвердить ФИО не удалось, и просит указать его полностью.
     *
     * @param chatId идентификатор чата Telegram
     */
    private void sendNameConfirmationFailure(Long chatId) {
        sendSimpleMessage(chatId, NAME_CONFIRMATION_MISSING_MESSAGE);
    }

    /**
     * Сообщает пользователю, что требуется поделиться контактом.
     *
     * @param chatId идентификатор чата Telegram
     */
    private void remindContactRequired(Long chatId) {
        transitionToState(chatId, BuyerChatState.AWAITING_CONTACT);
        sendPhoneRequestMessage(chatId,
                "📱 Пожалуйста, поделитесь контактом через кнопку ниже — только так мы сможем принять номер. После получения телефона мы продолжим настройку.");
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
                || "Неверно".equalsIgnoreCase(text)
                || "Изменить".equalsIgnoreCase(text);
    }

    /**
     * Фиксирует новое состояние сценария для указанного чата.
     * <p>
     * При переводе в режим ожидания контакта дополнительно помечает, что
     * постоянная клавиатура скрыта и должна быть заменена кнопкой запроса номера.
     * </p>
     *
     * @param chatId идентификатор чата Telegram
     * @param state  состояние, в которое нужно перевести сценарий
     */
    private void transitionToState(Long chatId, BuyerChatState state) {
        if (chatId == null || state == null) {
            return;
        }

        chatSessionRepository.updateState(chatId, state);

        if (state == BuyerChatState.AWAITING_CONTACT) {
            chatSessionRepository.markKeyboardHidden(chatId);
        }
    }

    /**
     * Возвращает зафиксированное состояние чата.
     *
     * @param chatId идентификатор чата Telegram
     * @return сохранённое состояние или {@link BuyerChatState#IDLE}, если чат не отслеживается
     */
    BuyerChatState getState(Long chatId) {
        return chatSessionRepository.getState(chatId);
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
        if (chatId == null) {
            return;
        }

        Optional<Customer> optional = telegramService.findByChatId(chatId);
        Customer customer = optional.orElse(null);
        String text = buildMainMenuText(customer);
        InlineKeyboardMarkup markup = buildMainMenuKeyboard(customer);
        sendInlineMessage(chatId, text, markup, BuyerBotScreen.MENU);

        ensurePersistentKeyboard(chatId);
    }

    /**
     * Формирует текст якорного сообщения главного меню в зависимости от состояния покупателя.
     *
     * @param customer покупатель, для которого отображается меню
     * @return текст для отображения в сообщении меню
     */
    private String buildMainMenuText(Customer customer) {
        StringBuilder builder = new StringBuilder();
        builder.append("📋 Главное меню\n\n");

        if (customer == null) {
            builder.append("Поделитесь номером телефона командой /start, чтобы получать уведомления и статистику.\n\n");
        } else {
            builder.append("Уведомления: ")
                    .append(customer.isNotificationsEnabled() ? "включены" : "отключены")
                    .append('\n');

            String fullName = customer.getFullName();
            if (fullName == null || fullName.isBlank()) {
                builder.append("Имя: не указано");
            } else if (customer.getNameSource() == NameSource.USER_CONFIRMED) {
                builder.append("Имя: ").append(fullName).append(" (подтверждено)");
            } else {
                builder.append("Имя: ").append(fullName).append(" (ожидает подтверждения)");
            }
            builder.append("\n\n");
        }

        builder.append("Выберите раздел через кнопки ниже или воспользуйтесь клавишами «🏠 Меню» и «❓ Помощь» на клавиатуре.");
        return builder.toString();
    }

    /**
     * Создаёт инлайн-клавиатуру главного меню.
     *
     * @param customer покупатель, для которого строится клавиатура (резерв на будущие условия)
     * @return клавиатура с основными разделами
     */
    private InlineKeyboardMarkup buildMainMenuKeyboard(Customer customer) {
        InlineKeyboardButton statsButton = InlineKeyboardButton.builder()
                .text(BUTTON_STATS)
                .callbackData(CALLBACK_MENU_SHOW_STATS)
                .build();
        InlineKeyboardButton settingsButton = InlineKeyboardButton.builder()
                .text(BUTTON_SETTINGS)
                .callbackData(CALLBACK_MENU_SHOW_SETTINGS)
                .build();
        InlineKeyboardButton helpButton = InlineKeyboardButton.builder()
                .text(BUTTON_HELP)
                .callbackData(CALLBACK_MENU_SHOW_HELP)
                .build();

        List<InlineKeyboardRow> rows = new ArrayList<>();
        rows.add(new InlineKeyboardRow(statsButton, settingsButton));
        rows.add(new InlineKeyboardRow(helpButton));

        if (customer != null) {
            String fullName = customer.getFullName();
            boolean hasName = fullName != null && !fullName.isBlank();
            if (hasName && customer.getNameSource() != NameSource.USER_CONFIRMED) {
                InlineKeyboardButton confirmButton = InlineKeyboardButton.builder()
                        .text("✅ Подтвердить имя")
                        .callbackData(CALLBACK_NAME_CONFIRM)
                        .build();
                InlineKeyboardButton editButton = InlineKeyboardButton.builder()
                        .text("✏️ Изменить имя")
                        .callbackData(CALLBACK_NAME_EDIT)
                        .build();
                rows.add(new InlineKeyboardRow(confirmButton, editButton));
            }
        }

        return InlineKeyboardMarkup.builder()
                .keyboard(rows)
                .build();
    }

    /**
     * Формирует инлайн-клавиатуру для подтверждения или изменения имени.
     *
     * @return клавиатура с действиями по управлению именем
     */
    private InlineKeyboardMarkup buildNameConfirmationKeyboard() {
        InlineKeyboardButton confirmButton = InlineKeyboardButton.builder()
                .text("✅ Подтвердить имя")
                .callbackData(CALLBACK_NAME_CONFIRM)
                .build();
        InlineKeyboardButton editButton = InlineKeyboardButton.builder()
                .text("✏️ Изменить имя")
                .callbackData(CALLBACK_NAME_EDIT)
                .build();

        List<InlineKeyboardRow> rows = new ArrayList<>();
        rows.add(new InlineKeyboardRow(confirmButton, editButton));
        rows.add(new InlineKeyboardRow(buildBackButton()));

        return InlineKeyboardMarkup.builder()
                .keyboard(rows)
                .build();
    }

    /**
     * Создаёт постоянную reply-клавиатуру с быстрыми действиями меню.
     *
     * @return разметка reply-клавиатуры
     */
    private ReplyKeyboardMarkup createPersistentMenuKeyboard() {
        KeyboardButton menuButton = new KeyboardButton(BUTTON_MENU);
        KeyboardButton helpButton = new KeyboardButton(BUTTON_HELP);
        KeyboardRow row = new KeyboardRow(List.of(menuButton, helpButton));

        ReplyKeyboardMarkup markup = new ReplyKeyboardMarkup(List.of(row));
        markup.setResizeKeyboard(true);
        markup.setOneTimeKeyboard(false);
        return markup;
    }

    /**
     * Фиксирует факт скрытия постоянной клавиатуры пользователем.
     *
     * @param chatId  идентификатор чата Telegram
     * @param message входящее сообщение пользователя
     * @return {@code true}, если клавиатура была скрыта в рамках текущего сообщения
     */
    private boolean detectPersistentKeyboardRemoval(Long chatId, Message message) {
        if (chatId == null || message == null) {
            return false;
        }

        JsonNode messageJson;
        try {
            messageJson = objectMapper.convertValue(message, JsonNode.class);
        } catch (IllegalArgumentException e) {
            return false;
        }

        if (messageJson == null || messageJson.isNull() || messageJson.isMissingNode()) {
            return false;
        }

        JsonNode replyMarkupNode = messageJson.path("reply_markup");
        if (!replyMarkupNode.isMissingNode() && replyMarkupNode.path("remove_keyboard").asBoolean(false)) {
            chatSessionRepository.markKeyboardHidden(chatId);
            return true;
        }

        return false;
    }

    /**
     * Возвращает меню-клавиатуру, если она была скрыта ранее.
     * <p>
     * В режиме ожидания контакта клавиатура не восстанавливается, чтобы
     * пользователь видел только кнопку отправки номера телефона.
     * </p>
     *
     * @param chatId            идентификатор чата Telegram
     * @param skipCurrentUpdate {@code true}, если клавиатура скрыта прямо сейчас и её не нужно восстанавливать немедленно
     */
    private void restorePersistentKeyboardIfNeeded(Long chatId, boolean skipCurrentUpdate) {
        if (chatId == null || skipCurrentUpdate) {
            return;
        }

        if (!chatSessionRepository.isKeyboardHidden(chatId)) {
            return;
        }

        if (getState(chatId) == BuyerChatState.AWAITING_CONTACT) {
            return;
        }

        ensurePersistentKeyboard(chatId);
    }

    /**
     * Обеспечивает наличие постоянной reply-клавиатуры внизу диалога.
     * <p>
     * Сообщение, которое содержит клавиатуру, остаётся последним, чтобы кнопки
     * «🏠 Меню» и «❓ Помощь» были доступны даже после перезапуска бота и ручного
     * скрытия клавиатуры пользователем. Во время ожидания контакта клавиатура не
     * восстанавливается, чтобы не мешать сценарию отправки номера телефона.
     * </p>
     *
     * @param chatId идентификатор чата Telegram
     */
    private void ensurePersistentKeyboard(Long chatId) {
        if (chatId == null) {
            return;
        }

        if (getState(chatId) == BuyerChatState.AWAITING_CONTACT) {
            return;
        }

        if (!chatSessionRepository.isKeyboardHidden(chatId)) {
            return;
        }

        SendMessage message = new SendMessage(chatId.toString(),
                "Клавиши быстрого доступа доступны на панели ниже: «🏠 Меню» и «❓ Помощь».");
        message.setReplyMarkup(createPersistentMenuKeyboard());
        message.setDisableNotification(true);

        try {
            Message sent = telegramClient.execute(message);
            chatSessionRepository.markKeyboardVisible(chatId);
            if (sent == null) {
                log.debug("ℹ️ Telegram не вернул данные отправленного сообщения для чата {}", chatId);
            }
        } catch (TelegramApiException e) {
            chatSessionRepository.markKeyboardHidden(chatId);
            log.error("❌ Ошибка применения reply-клавиатуры", e);
        }
    }

    /**
     * Показывает или обновляет якорное сообщение, сохраняя данные в устойчивом хранилище.
     *
     * @param chatId идентификатор чата Telegram
     * @param text   текст, который необходимо отобразить
     * @param markup инлайн-клавиатура для сообщения
     * @param screen экран, который следует зафиксировать для последующего восстановления
     */
    private void sendInlineMessage(Long chatId,
                                   String text,
                                   InlineKeyboardMarkup markup,
                                   BuyerBotScreen screen) {
        if (chatId == null) {
            return;
        }

        Integer messageId = chatSessionRepository.find(chatId)
                .map(ChatSession::getAnchorMessageId)
                .orElse(null);

        if (messageId != null) {
            EditMessageText edit = EditMessageText.builder()
                    .chatId(chatId.toString())
                    .messageId(messageId)
                    .text(text)
                    .replyMarkup(markup)
                    .build();
            try {
                telegramClient.execute(edit);
                chatSessionRepository.updateAnchorAndScreen(chatId, messageId, screen);
                return;
            } catch (TelegramApiException e) {
                String errorMessage = e.getMessage();
                if (errorMessage != null && errorMessage.contains("message is not modified")) {
                    chatSessionRepository.updateAnchorAndScreen(chatId, messageId, screen);
                    log.debug("ℹ️ Содержимое якорного сообщения для чата {} не изменилось", chatId);
                    return;
                }
                log.warn("⚠️ Не удалось обновить якорное сообщение для чата {}", chatId, e);
                chatSessionRepository.clearAnchor(chatId);
                chatSessionRepository.markKeyboardHidden(chatId);
            }
        }

        SendMessage message = new SendMessage(chatId.toString(), text);
        message.setReplyMarkup(markup);
        message.setDisableNotification(true);
        try {
            Message sent = telegramClient.execute(message);
            if (sent != null) {
                chatSessionRepository.updateAnchorAndScreen(chatId, sent.getMessageId(), screen);
            }
        } catch (TelegramApiException e) {
            log.error("❌ Ошибка отправки якорного сообщения", e);
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
     * <p>
     * Дополнительно фиксирует, что постоянная клавиатура скрыта, чтобы при возврате в меню её переотправить.
     * </p>
     *
     * @param chatId идентификатор чата
     * @param text   текст, который увидит пользователь
     */
    private void sendPhoneRequestMessage(Long chatId, String text) {
        if (chatId == null) {
            return;
        }

        chatSessionRepository.markKeyboardHidden(chatId);
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
        transitionToState(chatId, BuyerChatState.AWAITING_NAME_INPUT);
        sendSimpleMessage(chatId, "✍️ Пожалуйста, укажите своё ФИО");
    }

    /**
     * Проверяет сохранённое ФИО покупателя и при некорректном значении запрашивает его повторно.
     *
     * @param chatId   идентификатор чата Telegram
     * @param customer сущность покупателя с сохранёнными данными
     * @return {@code true}, если сохранённое ФИО валидно и может быть показано
     */
    private boolean ensureValidStoredNameOrRequestUpdate(Long chatId, Customer customer) {
        if (customer == null) {
            return true;
        }

        String fullName = customer.getFullName();
        if (fullName == null) {
            return true;
        }

        FullNameValidator.FullNameValidationResult validation = fullNameValidator.validate(fullName);
        if (validation.valid()) {
            return true;
        }

        telegramService.markNameUnconfirmed(chatId);
        customer.setNameSource(NameSource.MERCHANT_PROVIDED);
        transitionToState(chatId, BuyerChatState.AWAITING_NAME_INPUT);
        sendSimpleMessage(chatId, "Укажите своё ФИО");
        return false;
    }

    /**
     * Отправить пользователю ФИО из системы для подтверждения.
     *
     * @param chatId   идентификатор чата
     * @param fullName имя, известное системе
     */
    private void sendNameConfirmation(Long chatId, String fullName) {
        String text = String.format("У нас указано ваше ФИО: %s\nЭто верно?", fullName);
        sendInlineMessage(chatId, text, buildNameConfirmationKeyboard(), BuyerBotScreen.NAME_CONFIRMATION);
    }

    /**
     * Перерисовывает экран подтверждения ФИО на основе актуальных данных покупателя.
     *
     * @param chatId идентификатор чата Telegram
     */
    private void renderNameConfirmationScreen(Long chatId) {
        Optional<Customer> optional = telegramService.findByChatId(chatId);
        if (optional.isEmpty()) {
            sendMainMenu(chatId);
            return;
        }

        Customer customer = optional.get();
        if (!ensureValidStoredNameOrRequestUpdate(chatId, customer)) {
            return;
        }

        String fullName = customer.getFullName();
        if (fullName == null || fullName.isBlank()) {
            sendMainMenu(chatId);
            return;
        }

        sendNameConfirmation(chatId, fullName);
    }

    /**
     * Извлекает идентификатор отправителя из сообщения, если Telegram предоставил эти данные.
     *
     * @param message исходное сообщение с контактными данными
     * @return идентификатор пользователя или {@code null}, если он отсутствует
     */
    private Long extractSenderId(Message message) {
        if (message == null || message.getFrom() == null) {
            return null;
        }
        return message.getFrom().getId();
    }

    /**
     * Проверяет, что контакт однозначно принадлежит отправителю по идентификаторам Telegram.
     *
     * @param senderId      идентификатор отправителя сообщения
     * @param contactUserId идентификатор владельца контакта
     * @return {@code true}, если оба идентификатора присутствуют и совпадают
     */
    private boolean isContactOwnedBySender(Long senderId, Long contactUserId) {
        if (senderId == null || contactUserId == null) {
            return false;
        }
        return contactUserId.equals(senderId);
    }

    /**
     * Сообщает пользователю, что не удалось подтвердить владение номером, и повторно запрашивает контакт.
     *
     * @param chatId идентификатор чата Telegram
     */
    private void sendContactOwnershipRejectedMessage(Long chatId) {
        sendPhoneRequestMessage(chatId,
                "❌ Не удалось подтвердить, что номер принадлежит вам. Пожалуйста, поделитесь своим номером через кнопку ниже.");
    }

    /**
     * Обработать контакт с номером телефона от пользователя.
     * <p>
     * Привязывает номер к покупателю, подтверждает Telegram и предлагает
     * подтвердить или указать ФИО. Если контакт принадлежит другому аккаунту
     * либо Telegram не предоставил идентификаторы для проверки владения,
     * бот откажет в обработке и повторно попросит номер.
     * </p>
     *
     * @param chatId  идентификатор чата Telegram
     * @param message исходное сообщение с контактными данными
     * @param contact объект контакта с номером телефона
     */
    private void handleContact(Long chatId, Message message, Contact contact) {
        if (chatId == null || contact == null) {
            return;
        }

        Long senderId = extractSenderId(message);
        Long contactUserId = contact.getUserId();

        if (!isContactOwnedBySender(senderId, contactUserId)) {
            log.warn("⚠️ Не удалось подтвердить владение номером: chatId={}, contactUserId={}, senderId={}",
                    chatId, contactUserId, senderId);
            transitionToState(chatId, BuyerChatState.AWAITING_CONTACT);
            sendContactOwnershipRejectedMessage(chatId);
            return;
        }

        String rawPhone = contact.getPhoneNumber();
        String phone = PhoneUtils.normalizePhone(rawPhone);

        try {
            Customer customer = telegramService.linkTelegramToCustomer(phone, chatId);

            if (customer.isTelegramConfirmed()) {
                sendKeyboardRemovalMessage(chatId, "✅ Номер уже подтверждён. Обновляю меню...");
            } else {
                sendKeyboardRemovalMessage(chatId, "✅ Номер сохранён. Спасибо!");
                telegramService.confirmTelegram(customer);
                telegramService.notifyActualStatuses(customer);
            }

            transitionToState(chatId, BuyerChatState.IDLE);
            sendMainMenu(chatId);

            if (!ensureValidStoredNameOrRequestUpdate(chatId, customer)) {
                return;
            }

            String fullName = customer.getFullName();
            if (fullName != null && !fullName.isBlank()) {
                if (customer.getNameSource() != NameSource.USER_CONFIRMED) {
                    sendNameConfirmation(chatId, fullName);
                }
            } else {
                promptForName(chatId);
                return;
            }
        } catch (Exception e) {
            log.error("❌ Ошибка регистрации телефона {} для чата {}",
                    PhoneUtils.maskPhone(phone), chatId, e);
        }
    }

    /**
     * Отправляет сообщение с удалением временной клавиатуры, скрывая кнопку «📱 Поделиться номером».
     * <p>
     * Метод уведомляет пользователя о результате обработки контакта и фиксирует факт скрытия
     * клавиатуры в репозитории сессий, чтобы последующее меню смогло вернуть постоянные кнопки.
     * </p>
     *
     * @param chatId идентификатор чата Telegram
     * @param text   текст сообщения для пользователя (если пустой, применяется дефолтная фраза)
     */
    private void sendKeyboardRemovalMessage(Long chatId, String text) {
        if (chatId == null) {
            return;
        }

        chatSessionRepository.markKeyboardHidden(chatId);

        String safeText = (text == null || text.isBlank())
                ? "⌨️ Клавиатура скрыта. Меню появится в следующем сообщении."
                : text;

        SendMessage removalMessage = new SendMessage(chatId.toString(), safeText);
        ReplyKeyboardRemove removeMarkup = ReplyKeyboardRemove.builder()
                .removeKeyboard(true)
                .selective(false)
                .build();
        removalMessage.setReplyMarkup(removeMarkup);

        if (text == null || text.isBlank()) {
            removalMessage.setDisableNotification(true);
        }

        try {
            telegramClient.execute(removalMessage);
        } catch (TelegramApiException e) {
            log.error("❌ Ошибка скрытия клавиатуры в чате {}", chatId, e);
        }
    }
}