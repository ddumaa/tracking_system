package com.project.tracking_system.service.telegram;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.tracking_system.dto.ActionRequiredReturnRequestDto;
import com.project.tracking_system.dto.CustomerStatisticsDTO;
import com.project.tracking_system.dto.ReturnRequestUpdateResponse;
import com.project.tracking_system.dto.TelegramParcelInfoDTO;
import com.project.tracking_system.dto.TelegramParcelsOverviewDTO;
import com.project.tracking_system.entity.*;
import com.project.tracking_system.service.admin.AdminNotificationService;
import com.project.tracking_system.service.customer.CustomerTelegramService;
import com.project.tracking_system.utils.PhoneUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.security.access.AccessDeniedException;
import org.telegram.telegrambots.longpolling.interfaces.LongPollingUpdateConsumer;
import org.telegram.telegrambots.longpolling.starter.SpringLongPollingBot;
import org.telegram.telegrambots.longpolling.util.LongPollingSingleThreadUpdateConsumer;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.ParseMode;
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

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.Objects;
import java.util.function.Function;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.nio.charset.StandardCharsets;

/**
 * Telegram-бот для покупателей.
 */
@Component
@Slf4j
public class BuyerTelegramBot implements SpringLongPollingBot, LongPollingSingleThreadUpdateConsumer, TelegramAnnouncementSender {

    private static final String BUTTON_STATS = "📊 Статистика";
    private static final String BUTTON_PARCELS = "📦 Мои посылки";
    private static final String BUTTON_PARCELS_DELIVERED = "📬 Полученные";
    private static final String BUTTON_PARCELS_AWAITING = "🏬 Ждут забора";
    private static final String BUTTON_PARCELS_TRANSIT = "🚚 В пути";
    private static final String BUTTON_RETURNS = "🔁 Возвраты и обмены";
    private static final String BUTTON_RETURNS_ACTIVE = "📂 Текущие заявки";
    private static final String BUTTON_RETURNS_CREATE = "🆕 Создать заявку";
    private static final String BUTTON_RETURNS_DONE = "Хорошо";
    private static final String BUTTON_SETTINGS = "⚙️ Настройки";
    private static final String BUTTON_HELP = "❓ Помощь";
    private static final String BUTTON_MENU = "🏠 Меню";
    private static final String BUTTON_BACK = "⬅️ Назад";
    private static final String BUTTON_OUTCOME_OK = "Ок";
    private static final String BUTTON_OUTCOME_BACK = "Назад";

    private static final String CALLBACK_BACK_TO_MENU = "menu:back";
    private static final String CALLBACK_MENU_SHOW_STATS = "menu:stats";
    private static final String CALLBACK_MENU_SHOW_PARCELS = "menu:parcels";
    private static final String CALLBACK_MENU_RETURNS_EXCHANGES = "menu:returns";
    private static final String CALLBACK_MENU_SHOW_SETTINGS = "menu:settings";
    private static final String CALLBACK_MENU_SHOW_HELP = "menu:help";
    private static final String CALLBACK_PARCELS_DELIVERED = "parcels:delivered";
    private static final String CALLBACK_PARCELS_AWAITING = "parcels:awaiting";
    private static final String CALLBACK_PARCELS_TRANSIT = "parcels:transit";
    private static final String CALLBACK_RETURNS_SHOW_ACTIVE = "returns:active";
    private static final String CALLBACK_RETURNS_CREATE_REQUEST = "returns:create";
    private static final String CALLBACK_RETURNS_CREATE_TYPE_RETURN = "returns:create:type:return";
    private static final String CALLBACK_RETURNS_CREATE_TYPE_EXCHANGE = "returns:create:type:exchange";
    private static final String CALLBACK_RETURNS_CREATE_STORE_PREFIX = "returns:create:store:";
    private static final String CALLBACK_RETURNS_CREATE_PARCEL_PREFIX = "returns:create:parcel:";
    private static final String CALLBACK_RETURNS_REASON_PREFIX = "returns:create:reason:";
    private static final String CALLBACK_RETURNS_REASON_NOT_FIT = CALLBACK_RETURNS_REASON_PREFIX + "not_fit";
    private static final String CALLBACK_RETURNS_REASON_DEFECT = CALLBACK_RETURNS_REASON_PREFIX + "defect";
    private static final String CALLBACK_RETURNS_REASON_DISLIKE = CALLBACK_RETURNS_REASON_PREFIX + "dislike";
    private static final String CALLBACK_RETURNS_REASON_OTHER = CALLBACK_RETURNS_REASON_PREFIX + "other";
    private static final String CALLBACK_RETURNS_ACTIVE_SELECT_PREFIX = "returns:active:select:";
    private static final String CALLBACK_RETURNS_ACTIVE_TRACK_PREFIX = "returns:active:track:";
    private static final String CALLBACK_RETURNS_ACTIVE_COMMENT_PREFIX = "returns:active:comment:";
    private static final String CALLBACK_RETURNS_ACTIVE_CANCEL_PREFIX = "returns:active:cancel:";
    private static final String CALLBACK_RETURNS_ACTIVE_CANCEL_EXCHANGE_PREFIX = "returns:active:cancel_exchange:";
    private static final String CALLBACK_RETURNS_ACTIVE_CONVERT_PREFIX = "returns:active:convert:";
    private static final String CALLBACK_RETURNS_ACTIVE_BACK_TO_LIST = "returns:active:list";
    private static final String CALLBACK_RETURNS_DONE = "returns:done";
    private static final String CALLBACK_SETTINGS_TOGGLE_NOTIFICATIONS = "settings:toggle_notifications";
    private static final String CALLBACK_SETTINGS_CONFIRM_NAME = "settings:confirm_name";
    private static final String CALLBACK_SETTINGS_EDIT_NAME = "settings:edit_name";
    private static final String CALLBACK_NAME_CONFIRM = "name:confirm";
    private static final String CALLBACK_NAME_EDIT = "name:edit";
    private static final String CALLBACK_ANNOUNCEMENT_ACK = "announcement:ack";
    private static final String CALLBACK_NAVIGATE_BACK = "nav:back";

    private static final String NO_PARCELS_PLACEHOLDER = "• нет посылок";

    private static final String PARCEL_RETURN_FLOW_STARTED =
            "📩 Начинаем оформление возврата по посылке %s. Выберите, пожалуйста, причину ниже.";
    private static final String PARCEL_RETURN_REASON_REMINDER =
            "⚠️ Пожалуйста, выберите причину с помощью кнопок ниже.";
    private static final String PARCEL_RETURN_REASON_SELECTED_ACK = "Причина выбрана";
    private static final String RETURN_REASON_LABEL_NOT_FIT = "Не подошло";
    private static final String RETURN_REASON_LABEL_DEFECT = "Брак";
    private static final String RETURN_REASON_LABEL_DISLIKE = "Не понравилось";
    private static final String RETURN_REASON_LABEL_OTHER = "Другое";
    private static final String PARCEL_RETURN_FINISHED_TEMPLATE =
            "✅ Зафиксировали запрос на возврат посылки %s.\n• Причина: %s\n• Дата обращения: %s\nℹ️ Если трек появится позже, добавьте его через раздел «📂 Текущие заявки».";
    private static final String PARCEL_RETURN_NO_COMMENT = "без комментария";
    private static final String PARCEL_RETURN_NO_TRACK = "не указан";
    private static final String PARCEL_RETURN_DATE_UNKNOWN = "не указана";
    private static final String PARCEL_RETURN_REASON_UNKNOWN = "не указана";
    private static final String PARCEL_RETURN_ALREADY_REGISTERED_TEMPLATE =
            "ℹ️ По посылке %s уже оформлена активная заявка. Мы держим её на контроле.";
    private static final String PARCEL_RETURN_STEP_ACK = "Продолжаем оформление";
    private static final String PARCEL_EXCHANGE_REASON_REMINDER =
            "⚠️ Пожалуйста, выберите причину обмена с помощью кнопок ниже.";
    private static final String PARCEL_RETURN_CONTEXT_LOST =
            "⚠️ Не удалось восстановить контекст возврата. Повторите попытку через раздел \"🔁 Возвраты и обмены\" → «🆕 Создать заявку».";
    private static final DateTimeFormatter PARCEL_RETURN_DATE_FORMAT = DateTimeFormatter.ofPattern("d.MM.yyyy");
    private static final String PARCEL_RETURN_STATUS_INVALID =
            "⚠️ Вернуть можно только посылку со статусом «📬 Получена». Если статус ещё не обновился, попробуйте позже.";
    private static final String PARCEL_RETURN_ACCESS_DENIED =
            "⚠️ Не удалось подтвердить владельца посылки. Убедитесь, что она отображается в разделе «🔁 Возвраты и обмены» → «🆕 Создать заявку».";
    private static final String PARCEL_RETURN_REGISTRATION_FAILED =
            "⚠️ Не удалось зафиксировать заявку. Попробуйте ещё раз позже или обратитесь в поддержку.";
    private static final String PARCEL_RETURN_IDEMPOTENCY_CONFLICT =
            "⚠️ Похоже, что данные заявки отличаются от предыдущих. Свяжитесь с поддержкой для проверки.";
    private static final String PARCEL_EXCHANGE_FAILED =
            "⚠️ Не удалось запустить обмен. Попробуйте ещё раз позже или обратитесь в поддержку.";

    private static final String RETURNS_MENU_TEXT =
            "🔁 Возвраты и обмены\n\nВыберите действие:";
    private static final String RETURNS_ACTIVE_TITLE = "📂 Текущие заявки";
    private static final String RETURNS_ACTIVE_EMPTY_PLACEHOLDER = "• активных заявок нет";
    private static final String RETURNS_ACTIVE_CONTACT_HINT = "📱 Привяжите номер телефона командой /start, чтобы видеть активные заявки в этом разделе.";
    private static final String RETURNS_CREATE_TITLE = "🆕 Создание заявки";
    private static final String RETURNS_CREATE_CONTACT_HINT = "📱 Привяжите номер телефона командой /start, чтобы оформить заявку.";
    private static final String RETURNS_CREATE_TYPE_PROMPT = "Выберите тип заявки:";
    private static final String RETURNS_CREATE_TYPE_RETURN_LABEL = "↩️ Возврат";
    private static final String RETURNS_CREATE_TYPE_EXCHANGE_LABEL = "🔄 Обмен";
    private static final String RETURNS_CREATE_STORE_PROMPT = "Вы выбрали %s. Теперь укажите магазин:";
    private static final String RETURNS_CREATE_PARCEL_PROMPT = "Выберите посылку из магазина %s:";
    private static final String RETURNS_CREATE_NO_PARCELS =
            "⚠️ Подходящих посылок пока нет. Попробуйте позже, когда статус обновится.";
    private static final String RETURNS_CREATE_TYPE_ACK = "Тип выбран";
    private static final String RETURNS_CREATE_STORE_ACK = "Магазин выбран";
    private static final String RETURNS_CREATE_PARCEL_ACK = "Посылка выбрана";
    private static final String RETURNS_CREATE_REPEAT_HINT =
            "⚠️ Выберите вариант с помощью кнопок под сообщением.";
    private static final String PARCEL_ACTION_BLOCKED_TEXT = "заявка в обработке";

    private static final String RETURNS_ACTIVE_SELECT_PROMPT =
            "Выберите заявку с помощью кнопок ниже.";
    private static final String RETURNS_ACTIVE_SELECTED_HEADER_TEMPLATE = "Текущая заявка на %s";
    private static final String RETURNS_ACTIVE_DETAILS_TEMPLATE =
            "*Трек:* %s\n*Магазин:* %s\n*Статус:* %s\n*Дата обращения:* %s\n*Причина:* %s\n*Комментарий:* %s\n*Обратный трек:* %s";
    private static final String RETURNS_ACTIVE_ACTIONS_RETURN =
            "Доступные действия для возврата:";
    private static final String RETURNS_ACTIVE_ACTIONS_EXCHANGE =
            "Доступные действия для обмена:";
    private static final String RETURNS_ACTIVE_ACTION_NOT_AVAILABLE = "Заявка не найдена";
    private static final String RETURNS_ACTIVE_ACTION_FAILED =
            "⚠️ Не удалось обновить заявку. Попробуйте ещё раз позже или обратитесь в поддержку.";
    private static final String RETURNS_ACTIVE_TRACK_PROMPT =
            "✉️ Отправьте трек-номер обратной отправки или напишите «Нет», чтобы очистить поле.";
    private static final String RETURNS_ACTIVE_COMMENT_PROMPT =
            "💬 Напишите комментарий для менеджера. Если комментарий не нужен, отправьте «Нет».";
    private static final String RETURNS_ACTIVE_TRACK_SAVED =
            "✅ Трек-номер сохранён.";
    private static final String RETURNS_ACTIVE_COMMENT_SAVED =
            "✅ Комментарий сохранён.";
    private static final String RETURNS_ACTIVE_CANCEL_RETURN_SUCCESS =
            "ℹ️ Заявка на возврат отменена. Мы уведомим магазин.";
    private static final String RETURNS_ACTIVE_CANCEL_EXCHANGE_SUCCESS =
            "ℹ️ Обмен отменён. Мы уведомим магазин.";
    private static final String RETURNS_ACTIVE_CONVERT_SUCCESS =
            "✅ Заявка переведена в возврат. Вы сможете добавить трек позднее.";
    private static final String RETURNS_ACTIVE_NO_SELECTION =
            "⚠️ Выберите заявку перед выполнением действия.";
    private static final String RETURNS_ACTIVE_UPDATE_INVALID_TRACK =
            "⚠️ Укажите трек или напишите «Нет», чтобы очистить поле.";
    private static final String RETURNS_ACTIVE_COMMENT_INVALID =
            "⚠️ Комментарий не должен быть пустым. Напишите текст или «Нет» для очистки.";
    private static final String RETURNS_ACTIVE_UPDATE_FAILED =
            "⚠️ Не удалось сохранить изменения. Попробуйте ещё раз позже или обратитесь в поддержку.";
    private static final String BUTTON_RETURNS_BACK_TO_LIST = "↩️ Вернуться к списку";
    private static final String BUTTON_RETURNS_ACTION_TRACK = "📮 Указать трек";
    private static final String BUTTON_RETURNS_ACTION_COMMENT = "💬 Комментарий";
    private static final String BUTTON_RETURNS_ACTION_CANCEL_RETURN = "🚫 Отменить возврат";
    private static final String BUTTON_RETURNS_ACTION_CANCEL_EXCHANGE = "🚫 Отменить обмен";
    private static final String BUTTON_RETURNS_ACTION_CONVERT = "↩️ Перевести в возврат";

    private static final Base64.Encoder STORE_KEY_ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder STORE_KEY_DECODER = Base64.getUrlDecoder();

    private static final String TELEGRAM_PARSE_MODE = ParseMode.MARKDOWNV2;

    /**
     * Разделы списка посылок, где отображаются предупреждения и вспомогательные подписи.
     */
    private enum ParcelsSection {
        DELIVERED,
        WAITING_FOR_PICKUP,
        IN_TRANSIT,
        GENERIC
    }

    private static final String NAME_CONFIRMATION_MISSING_MESSAGE =
            "⚠️ Пока в системе нет ФИО для подтверждения. Пожалуйста, укажите его полностью.";
    private static final String NAME_EDIT_ANCHOR_TEXT =
            "✍️ Отправьте новое ФИО сообщением.\n\nПосле ввода воспользуйтесь кнопкой «🏠 Меню», чтобы вернуться.";

    private final TelegramClient telegramClient;
    private final CustomerTelegramService telegramService;
    private final AdminNotificationService adminNotificationService;
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
                            AdminNotificationService adminNotificationService,
                            FullNameValidator fullNameValidator,
                            ChatSessionRepository chatSessionRepository,
                            ObjectMapper objectMapper) {
        this.telegramClient = telegramClient;
        this.botToken = token;
        this.telegramService = telegramService;
        this.adminNotificationService = adminNotificationService;
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

        Long chatIdForActivity = extractChatId(update);
        Customer customerForActivity = null;
        if (chatIdForActivity != null) {
            customerForActivity = telegramService.findByChatId(chatIdForActivity)
                    .orElse(null);
            synchronizeAnnouncementState(chatIdForActivity, customerForActivity);
        }

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
            handleTextMessage(chatId, message.getText(), customerForActivity);
        }

        if (message.hasContact()) {
            handleContact(chatId, message, message.getContact());
        }
    }

    /**
     * Определяет идентификатор чата, связанный с обновлением Telegram.
     *
     * @param update входящее обновление Telegram
     * @return идентификатор чата или {@code null}, если его нельзя определить
     */
    private Long extractChatId(Update update) {
        if (update == null) {
            return null;
        }

        if (update.hasCallbackQuery()) {
            CallbackQuery callbackQuery = update.getCallbackQuery();
            if (callbackQuery != null) {
                MaybeInaccessibleMessage callbackMessage = callbackQuery.getMessage();
                if (callbackMessage != null) {
                    return callbackMessage.getChatId();
                }
            }
        }

        if (update.hasMessage()) {
            Message message = update.getMessage();
            if (message != null && message.getChat() != null) {
                return message.getChatId();
            }
        }

        if (update.hasMyChatMember()
                && update.getMyChatMember() != null
                && update.getMyChatMember().getChat() != null) {
            return update.getMyChatMember().getChat().getId();
        }

        return null;
    }

    /**
     * Синхронизирует состояние объявлений для подтверждённого покупателя.
     * <p>
     * Если активное объявление сменилось или администратор обновил его содержимое,
     * метод сохраняет новые метаданные и сбрасывает признак просмотра, чтобы баннер
     * вновь показался пользователю при следующем обращении.
     * </p>
     *
     * @param chatId   идентификатор чата Telegram
     * @param customer покупатель, привязанный к чату
     */
    private void synchronizeAnnouncementState(Long chatId, Customer customer) {
        if (chatId == null || customer == null || !customer.isTelegramConfirmed()) {
            return;
        }

        adminNotificationService.findActiveNotification()
                .ifPresent(notification -> {
                    ChatSession session = chatSessionRepository.find(chatId).orElse(null);
                    Long storedId = session != null ? session.getCurrentNotificationId() : null;
                    ZonedDateTime storedUpdatedAt = session != null ? session.getAnnouncementUpdatedAt() : null;

                    boolean shouldReset = storedId == null
                            || !notification.getId().equals(storedId)
                            || isNotificationRefreshed(notification.getUpdatedAt(), storedUpdatedAt);

                    if (shouldReset) {
                        Integer anchorId = session != null ? session.getAnchorMessageId() : null;
                        chatSessionRepository.updateAnnouncement(chatId,
                                notification.getId(),
                                anchorId,
                                notification.getUpdatedAt());
                    }
                });
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
    private void handleTextMessage(Long chatId, String text, Customer knownCustomer) {
        if (chatId == null || text == null) {
            return;
        }

        String trimmed = text.trim();

        if (BUTTON_MENU.equals(trimmed) || "/menu".equals(trimmed)) {
            handleMenuCommand(chatId);
            return;
        }

        BuyerChatState state = getState(chatId);

        if (state == BuyerChatState.AWAITING_CONTACT) {
            if ("/start".equals(trimmed)) {
                handleStartWhileAwaitingContact(chatId);
                return;
            }

            if (trimmed.isEmpty() || trimmed.startsWith("/")) {
                remindContactRequired(chatId);
                return;
            }

            handleAwaitedPhoneText(chatId, trimmed);
            return;
        }

        if ("/start".equals(trimmed)) {
            handleStartCommand(chatId, knownCustomer);
            return;
        }

        if (state == BuyerChatState.AWAITING_RETURN_REASON) {
            sendSimpleMessage(chatId, PARCEL_RETURN_REASON_REMINDER);
            return;
        }

        if (state == BuyerChatState.AWAITING_EXCHANGE_REASON) {
            sendSimpleMessage(chatId, PARCEL_EXCHANGE_REASON_REMINDER);
            return;
        }

        if (state == BuyerChatState.AWAITING_REQUEST_TYPE) {
            remindRequestTypeSelection(chatId);
            return;
        }

        if (state == BuyerChatState.AWAITING_STORE_SELECTION) {
            remindStoreSelection(chatId);
            return;
        }

        if (state == BuyerChatState.AWAITING_PARCEL_SELECTION) {
            remindParcelSelection(chatId);
            return;
        }

        if (state == BuyerChatState.AWAITING_ACTIVE_REQUEST_SELECTION) {
            remindRequestAction(chatId);
            return;
        }

        if (state == BuyerChatState.AWAITING_TRACK_UPDATE
                || state == BuyerChatState.AWAITING_COMMENT_UPDATE) {
            handleTrackUpdateInput(chatId, trimmed);
            return;
        }

        if (trimmed.isEmpty()) {
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

        boolean reasonNavigation = isReasonSelectionNavigation(data, session);

        if (CALLBACK_RETURNS_CREATE_TYPE_RETURN.equals(data)) {
            handleReturnRequestTypeSelection(chatId, callbackQuery, ReturnRequestType.RETURN);
            return;
        }

        if (CALLBACK_RETURNS_CREATE_TYPE_EXCHANGE.equals(data)) {
            handleReturnRequestTypeSelection(chatId, callbackQuery, ReturnRequestType.EXCHANGE);
            return;
        }

        if (data.startsWith(CALLBACK_RETURNS_CREATE_STORE_PREFIX)) {
            handleReturnRequestStoreSelection(chatId, callbackQuery, data);
            return;
        }

        if (data.startsWith(CALLBACK_RETURNS_CREATE_PARCEL_PREFIX)) {
            handleReturnRequestParcelSelection(chatId, callbackQuery, data);
            return;
        }

        if (data.startsWith(CALLBACK_RETURNS_REASON_PREFIX) || reasonNavigation) {
            boolean exchangeContext = session != null
                    && (session.getState() == BuyerChatState.AWAITING_EXCHANGE_REASON
                    || session.getLastScreen() == BuyerBotScreen.RETURNS_EXCHANGE_REASON);
            if (exchangeContext) {
                handleExchangeReasonCallback(chatId, callbackQuery, data);
            } else {
                handleReturnReasonCallback(chatId, callbackQuery, data);
            }
            return;
        }

        if (data.startsWith(CALLBACK_RETURNS_ACTIVE_SELECT_PREFIX)) {
            handleActiveRequestSelection(chatId, callbackQuery, data);
            return;
        }

        if (data.startsWith(CALLBACK_RETURNS_ACTIVE_TRACK_PREFIX)) {
            handleActiveRequestTrack(chatId, callbackQuery, data);
            return;
        }

        if (data.startsWith(CALLBACK_RETURNS_ACTIVE_COMMENT_PREFIX)) {
            handleActiveRequestComment(chatId, callbackQuery, data);
            return;
        }

        if (data.startsWith(CALLBACK_RETURNS_ACTIVE_CANCEL_PREFIX)) {
            handleActiveRequestCancelReturn(chatId, callbackQuery, data);
            return;
        }

        if (data.startsWith(CALLBACK_RETURNS_ACTIVE_CANCEL_EXCHANGE_PREFIX)) {
            handleActiveRequestCancelExchange(chatId, callbackQuery, data);
            return;
        }

        if (data.startsWith(CALLBACK_RETURNS_ACTIVE_CONVERT_PREFIX)) {
            handleActiveRequestConvert(chatId, callbackQuery, data);
            return;
        }

        if (CALLBACK_RETURNS_ACTIVE_BACK_TO_LIST.equals(data)) {
            handleActiveRequestBackToList(chatId, callbackQuery);
            return;
        }

        switch (data) {
            case CALLBACK_MENU_SHOW_STATS -> handleMenuOpenStats(chatId, callbackQuery);
            case CALLBACK_MENU_SHOW_PARCELS -> handleMenuOpenParcels(chatId, callbackQuery);
            case CALLBACK_MENU_RETURNS_EXCHANGES -> handleMenuOpenReturns(chatId, callbackQuery);
            case CALLBACK_MENU_SHOW_SETTINGS -> handleMenuOpenSettings(chatId, callbackQuery);
            case CALLBACK_MENU_SHOW_HELP -> handleMenuOpenHelp(chatId, callbackQuery);
            case CALLBACK_PARCELS_DELIVERED -> handleParcelsDeliveredCallback(chatId, callbackQuery);
            case CALLBACK_PARCELS_AWAITING -> handleParcelsAwaitingCallback(chatId, callbackQuery);
            case CALLBACK_PARCELS_TRANSIT -> handleParcelsTransitCallback(chatId, callbackQuery);
            case CALLBACK_RETURNS_SHOW_ACTIVE -> handleReturnsShowActive(chatId, callbackQuery);
            case CALLBACK_RETURNS_CREATE_REQUEST -> handleReturnsCreateRequest(chatId, callbackQuery);
            case CALLBACK_RETURNS_DONE -> handleReturnCompletionAcknowledgement(chatId, callbackQuery);
            case CALLBACK_ANNOUNCEMENT_ACK -> handleAnnouncementAcknowledgement(chatId, callbackQuery);
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
            case CALLBACK_NAVIGATE_BACK -> handleNavigateBack(chatId, callbackQuery);
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
     * Определяет, относится ли callback к навигации на шагах выбора причины возврата или обмена.
     *
     * @param data    данные callback-запроса
     * @param session сохранённая сессия пользователя
     * @return {@code true}, если нажата кнопка «Назад» или «Меню» на экране выбора причины
     */
    private boolean isReasonSelectionNavigation(String data, ChatSession session) {
        if (data == null) {
            return false;
        }
        if (!CALLBACK_NAVIGATE_BACK.equals(data) && !CALLBACK_BACK_TO_MENU.equals(data)) {
            return false;
        }
        BuyerBotScreen lastScreen = session != null ? session.getLastScreen() : null;
        return lastScreen == BuyerBotScreen.RETURNS_RETURN_REASON
                || lastScreen == BuyerBotScreen.RETURNS_EXCHANGE_REASON;
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
     * Сбрасывает якорное сообщение главного меню, если оно уже отображается пользователю.
     * <p>
     * Метод используется при повторном выборе пункта «🏠 Меню», чтобы бот заново отправил
     * сообщение с главной навигацией. При наличии предыдущего сообщения его инлайн-клавиатура
     * удаляется, чтобы пользователь не взаимодействовал с устаревшим экземпляром. При этом
     * сохраняется признак видимости постоянной клавиатуры, чтобы не отправлять лишние подсказки.
     * </p>
     *
     * @param chatId идентификатор чата Telegram
     */
    private void resetMenuAnchorIfAlreadyShown(Long chatId) {
        if (chatId == null) {
            return;
        }

        chatSessionRepository.find(chatId)
                .filter(session -> session.getLastScreen() == BuyerBotScreen.MENU)
                .ifPresent(session -> {
                    Integer anchorMessageId = session.getAnchorMessageId();
                    chatSessionRepository.deactivateAnchor(chatId);
                    if (anchorMessageId != null) {
                        removeInlineKeyboard(chatId, anchorMessageId);
                    }
                });
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
            case PARCELS -> sendParcelsScreen(chatId);
            case RETURNS_MENU -> sendReturnsMenuScreen(chatId);
            case RETURNS_ACTIVE_REQUESTS -> sendActiveReturnRequestsScreen(chatId);
            case RETURNS_CREATE_TYPE -> showReturnRequestTypeScreen(chatId);
            case RETURNS_CREATE_STORE -> {
                ChatSession session = ensureChatSession(chatId);
                ReturnRequestType type = Optional.ofNullable(session.getReturnRequestType())
                        .orElse(ReturnRequestType.RETURN);
                List<TelegramParcelInfoDTO> available = loadReturnableParcels(chatId);
                showReturnRequestStoreScreen(chatId, type, available);
            }
            case RETURNS_CREATE_REQUEST -> {
                ChatSession session = ensureChatSession(chatId);
                String storeName = session.getReturnStoreName();
                if (storeName == null) {
                    showReturnRequestTypeScreen(chatId);
                    return;
                }
                List<TelegramParcelInfoDTO> available = loadReturnableParcels(chatId);
                Map<String, List<TelegramParcelInfoDTO>> grouped = groupReturnableParcelsByStore(available);
                List<TelegramParcelInfoDTO> storeParcels = grouped.getOrDefault(storeName, List.of());
                if (storeParcels.isEmpty()) {
                    ReturnRequestType type = Optional.ofNullable(session.getReturnRequestType())
                            .orElse(ReturnRequestType.RETURN);
                    showReturnRequestStoreScreen(chatId, type, available);
                    return;
                }
                showReturnRequestParcelScreen(chatId, storeName, storeParcels);
            }
            case RETURNS_RETURN_REASON -> resendReturnReasonPrompt(chatId);
            case RETURNS_EXCHANGE_REASON -> resendExchangeReasonPrompt(chatId);
            case RETURNS_RETURN_COMPLETION -> sendReturnCompletionScreen(chatId);
            case RETURNS_EXCHANGE_COMPLETION -> sendExchangeCompletionScreen(chatId);
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
    private void handleStartCommand(Long chatId, Customer knownCustomer) {
        log.info("✅ Команда /start получена от {}", chatId);
        Optional<Customer> optional = knownCustomer != null
                ? Optional.of(knownCustomer)
                : telegramService.findByChatId(chatId);
        Customer customer = optional.orElse(null);
        synchronizeAnnouncementState(chatId, customer);
        if (optional.isEmpty()) {
            transitionToState(chatId, BuyerChatState.AWAITING_CONTACT);
            sendSharePhoneKeyboard(chatId);
            return;
        }

        BuyerChatState previousState = getState(chatId);
        transitionToState(chatId, BuyerChatState.IDLE);

        // Определяем, требуется ли переотправить постоянную клавиатуру после возврата в меню.
        boolean keyboardHidden = chatSessionRepository.isKeyboardHidden(chatId);
        boolean shouldResetKeyboardFlag = previousState == BuyerChatState.AWAITING_CONTACT || keyboardHidden;
        if (shouldResetKeyboardFlag) {
            chatSessionRepository.markKeyboardHidden(chatId);
        }
        sendMainMenu(chatId, customer);

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
     * Обрабатывает повторный вызов команды /start, когда бот уже ждёт контакт пользователя.
     * <p>
     * Метод проверяет, отправлялось ли ранее сообщение с просьбой поделиться номером.
     * Если сообщение ещё не отправлялось (например, сессия была восстановлена), бот повторно
     * напоминает о необходимости контакта. В остальных случаях команда игнорируется, чтобы
     * не дублировать приветствие и не засорять диалог.
     * </p>
     *
     * @param chatId идентификатор чата Telegram
     */
    private void handleStartWhileAwaitingContact(Long chatId) {
        if (chatId == null) {
            return;
        }

        boolean contactRequestSent = chatSessionRepository.isContactRequestSent(chatId);
        if (!contactRequestSent) {
            remindContactRequired(chatId);
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
     * Показывает раздел «Мои посылки» из главного меню.
     *
     * @param chatId        идентификатор чата Telegram
     * @param callbackQuery исходный callback-запрос
     */
    private void handleMenuOpenParcels(Long chatId, CallbackQuery callbackQuery) {
        if (chatId == null) {
            answerCallbackQuery(callbackQuery, "Команда недоступна");
            return;
        }
        answerCallbackQuery(callbackQuery, "Мои посылки");
        sendParcelsScreen(chatId);
    }

    /**
     * Показывает меню возвратов и обменов из главного меню.
     *
     * @param chatId        идентификатор чата Telegram
     * @param callbackQuery исходный callback-запрос
     */
    private void handleMenuOpenReturns(Long chatId, CallbackQuery callbackQuery) {
        if (chatId == null) {
            answerCallbackQuery(callbackQuery, "Команда недоступна");
            return;
        }
        answerCallbackQuery(callbackQuery, "Возвраты");
        sendReturnsMenuScreen(chatId);
    }

    /**
     * Отправляет экран меню возвратов, позволяющий выбрать нужный раздел.
     *
     * @param chatId идентификатор чата Telegram
     */
    private void sendReturnsMenuScreen(Long chatId) {
        String text = buildReturnsMenuText();
        List<BuyerBotScreen> navigationPath = computeNavigationPath(chatId, BuyerBotScreen.RETURNS_MENU);
        InlineKeyboardMarkup markup = buildReturnsMenuKeyboard(navigationPath);
        sendInlineMessage(chatId, text, markup, BuyerBotScreen.RETURNS_MENU, navigationPath);
    }

    /**
     * Формирует текст меню возвратов и обменов.
     *
     * @return текст сообщения с подсказками по разделам
     */
    private String buildReturnsMenuText() {
        return escapeMarkdown(RETURNS_MENU_TEXT);
    }

    /**
     * Создаёт клавиатуру меню возвратов с вариантами действий и навигацией.
     *
     * @param navigationPath путь навигации для отображения кнопок назад и меню
     * @return готовая клавиатура с пунктами меню
     */
    private InlineKeyboardMarkup buildReturnsMenuKeyboard(List<BuyerBotScreen> navigationPath) {
        InlineKeyboardButton activeButton = InlineKeyboardButton.builder()
                .text(BUTTON_RETURNS_ACTIVE)
                .callbackData(CALLBACK_RETURNS_SHOW_ACTIVE)
                .build();
        InlineKeyboardButton createButton = InlineKeyboardButton.builder()
                .text(BUTTON_RETURNS_CREATE)
                .callbackData(CALLBACK_RETURNS_CREATE_REQUEST)
                .build();

        List<InlineKeyboardRow> rows = new ArrayList<>();
        rows.add(new InlineKeyboardRow(activeButton));
        rows.add(new InlineKeyboardRow(createButton));
        appendNavigationRow(rows, navigationPath);

        return InlineKeyboardMarkup.builder()
                .keyboard(rows)
                .build();
    }

    /**
     * Показывает детальный список полученных посылок по нажатию соответствующей кнопки.
     *
     * @param chatId        идентификатор чата Telegram
     * @param callbackQuery исходный callback-запрос
     */
    private void handleParcelsDeliveredCallback(Long chatId, CallbackQuery callbackQuery) {
        handleParcelsCategoryCallback(chatId,
                callbackQuery,
                "Полученные",
                "📬 Полученные посылки",
                TelegramParcelsOverviewDTO::getDelivered,
                ParcelsSection.DELIVERED);
    }

    /**
     * Показывает детальный список посылок, ожидающих покупателя, при выборе категории.
     *
     * @param chatId        идентификатор чата Telegram
     * @param callbackQuery исходный callback-запрос
     */
    private void handleParcelsAwaitingCallback(Long chatId, CallbackQuery callbackQuery) {
        handleParcelsCategoryCallback(chatId,
                callbackQuery,
                "Ждут забора",
                "🏬 Посылки, ожидающие забора",
                TelegramParcelsOverviewDTO::getWaitingForPickup,
                ParcelsSection.WAITING_FOR_PICKUP);
    }

    /**
     * Показывает детальный список посылок в пути при выборе соответствующей категории.
     *
     * @param chatId        идентификатор чата Telegram
     * @param callbackQuery исходный callback-запрос
     */
    private void handleParcelsTransitCallback(Long chatId, CallbackQuery callbackQuery) {
        handleParcelsCategoryCallback(chatId,
                callbackQuery,
                "В пути",
                "🚚 Посылки в пути",
                TelegramParcelsOverviewDTO::getInTransit,
                ParcelsSection.IN_TRANSIT);
    }

    /**
     * Показывает активные заявки на возврат для покупателя.
     *
     * @param chatId        идентификатор чата Telegram
     * @param callbackQuery исходный callback-запрос
     */
    private void handleReturnsShowActive(Long chatId, CallbackQuery callbackQuery) {
        if (chatId == null) {
            answerCallbackQuery(callbackQuery, "Команда недоступна");
            return;
        }
        answerCallbackQuery(callbackQuery, "Текущие заявки");
        sendActiveReturnRequestsScreen(chatId);
    }

    /**
     * Показывает экран выбора посылки для новой заявки.
     *
     * @param chatId        идентификатор чата Telegram
     * @param callbackQuery исходный callback-запрос
     */
    private void handleReturnsCreateRequest(Long chatId, CallbackQuery callbackQuery) {
        if (chatId == null) {
            answerCallbackQuery(callbackQuery, "Команда недоступна");
            return;
        }
        answerCallbackQuery(callbackQuery, "Создать заявку");
        if (telegramService.findByChatId(chatId).isEmpty()) {
            List<BuyerBotScreen> navigationPath = computeNavigationPath(chatId, BuyerBotScreen.RETURNS_CREATE_TYPE);
            InlineKeyboardMarkup markup = buildNavigationKeyboard(navigationPath);
            String text = escapeMarkdown(RETURNS_CREATE_TITLE)
                    + "\n\n"
                    + escapeMarkdown(RETURNS_CREATE_CONTACT_HINT);
            sendInlineMessage(chatId, text, markup, BuyerBotScreen.RETURNS_CREATE_TYPE, navigationPath);
            remindContactRequired(chatId);
            return;
        }

        ChatSession session = ensureChatSession(chatId);
        session.clearReturnRequestData();
        session.setState(BuyerChatState.AWAITING_REQUEST_TYPE);
        chatSessionRepository.save(session);
        transitionToState(chatId, BuyerChatState.AWAITING_REQUEST_TYPE);
        showReturnRequestTypeScreen(chatId);
    }

    /**
     * Отображает экран с активными заявками возврата или обмена.
     * <p>
     * Метод обновляет навигационный путь в сессии, гарантируя наличие шага «Возвраты и обмены»,
     * благодаря чему кнопка «⬅️ Назад» всегда ведёт в меню возвратов. Если чат ещё не привязан к
     * покупателю, бот сообщает об этом и повторно отправляет запрос на номер телефона, не обращаясь
     * к сервису за данными.
     * </p>
     *
     * @param chatId идентификатор чата Telegram
     */
    private void sendActiveReturnRequestsScreen(Long chatId) {
        ChatSession session = ensureChatSession(chatId);
        session.updateNavigationForScreen(BuyerBotScreen.RETURNS_ACTIVE_REQUESTS, false);
        List<BuyerBotScreen> navigationPath = session.getNavigationPath();

        boolean customerMissing = telegramService.findByChatId(chatId).isEmpty();
        if (customerMissing) {
            session.setState(BuyerChatState.IDLE);
            chatSessionRepository.save(session);

            InlineKeyboardMarkup markup = buildNavigationKeyboard(navigationPath);
            String text = escapeMarkdown(RETURNS_ACTIVE_TITLE)
                    + "\n\n"
                    + escapeMarkdown(RETURNS_ACTIVE_CONTACT_HINT);
            sendInlineMessage(chatId, text, markup, BuyerBotScreen.RETURNS_ACTIVE_REQUESTS, navigationPath);
            remindContactRequired(chatId);
            return;
        }

        List<ActionRequiredReturnRequestDto> requests = telegramService.getReturnRequestsRequiringAction(chatId);
        ActionRequiredReturnRequestDto selected = resolveSelectedRequest(session, requests);

        session.setState(BuyerChatState.AWAITING_ACTIVE_REQUEST_SELECTION);
        chatSessionRepository.save(session);

        InlineKeyboardMarkup markup = buildActiveRequestsKeyboard(requests, selected, navigationPath);
        String text = buildActiveReturnRequestsMessage(requests, selected);
        sendInlineMessage(chatId, text, markup, BuyerBotScreen.RETURNS_ACTIVE_REQUESTS, navigationPath);

        transitionToState(chatId, BuyerChatState.AWAITING_ACTIVE_REQUEST_SELECTION);
    }

    private ActionRequiredReturnRequestDto resolveSelectedRequest(ChatSession session,
                                                                  List<ActionRequiredReturnRequestDto> requests) {
        if (session == null) {
            return null;
        }
        if (requests == null || requests.isEmpty()) {
            session.clearActiveReturnRequestContext();
            return null;
        }
        Long selectedId = session.getActiveReturnRequestId();
        if (selectedId == null) {
            return null;
        }
        return requests.stream()
                .filter(request -> selectedId.equals(request.requestId()))
                .findFirst()
                .orElseGet(() -> {
                    session.clearActiveReturnRequestContext();
                    return null;
                });
    }

    /**
     * Формирует сообщение с информацией об активных заявках на возврат и подсказками по выбранной заявке.
     */
    private String buildActiveReturnRequestsMessage(List<ActionRequiredReturnRequestDto> requests,
                                                    ActionRequiredReturnRequestDto selected) {
        StringBuilder builder = new StringBuilder();
        builder.append(escapeMarkdown(RETURNS_ACTIVE_TITLE))
                .append(System.lineSeparator())
                .append(System.lineSeparator());

        if (requests == null || requests.isEmpty()) {
            builder.append(RETURNS_ACTIVE_EMPTY_PLACEHOLDER);
            return builder.toString();
        }

        if (selected == null) {
            builder.append(escapeMarkdown(RETURNS_ACTIVE_SELECT_PROMPT));
            return builder.toString();
        }

        String requestTypeLabel = resolveRequestTypeLabel(selected);
        String selectedHeader = String.format(RETURNS_ACTIVE_SELECTED_HEADER_TEMPLATE, requestTypeLabel);
        builder.append('*')
                .append(escapeMarkdown(selectedHeader))
                .append('*')
                .append(System.lineSeparator());
        builder.append(formatSelectedRequestDetails(selected));
        builder.append(System.lineSeparator())
                .append(System.lineSeparator());
        String actionsHint = selected.status() == OrderReturnRequestStatus.EXCHANGE_APPROVED
                ? RETURNS_ACTIVE_ACTIONS_EXCHANGE
                : RETURNS_ACTIVE_ACTIONS_RETURN;
        builder.append(escapeMarkdown(actionsHint));
        return builder.toString();
    }

    /**
     * Определяет текстовое описание типа выбранной заявки для заголовка блока подробностей.
     *
     * @param request выбранная заявка из списка активных обращений
     * @return человеко-читаемое название типа: «возврат» или «обмен»
     */
    private String resolveRequestTypeLabel(ActionRequiredReturnRequestDto request) {
        if (request == null || request.status() != OrderReturnRequestStatus.EXCHANGE_APPROVED) {
            return "возврат";
        }
        return "обмен";
    }

    private String formatSelectedRequestDetails(ActionRequiredReturnRequestDto request) {
        String track = escapeMarkdown(formatTrackNumber(request.trackNumber()));
        String store = escapeMarkdown(resolveStoreLabel(request.storeName()));
        String status = escapeMarkdown(safeRequestStatus(request.statusLabel()));
        String date = escapeMarkdown(safeOrDash(request.requestedAt()));
        String reason = escapeMarkdown(request.reason() == null ? PARCEL_RETURN_REASON_UNKNOWN : request.reason());
        String commentValue = request.comment();
        String comment = escapeMarkdown(commentValue == null || commentValue.isBlank()
                ? PARCEL_RETURN_NO_COMMENT
                : commentValue);
        String reverse = escapeMarkdown(request.reverseTrackNumber() == null || request.reverseTrackNumber().isBlank()
                ? PARCEL_RETURN_NO_TRACK
                : request.reverseTrackNumber());
        return String.format(RETURNS_ACTIVE_DETAILS_TEMPLATE, track, store, status, date, reason, comment, reverse);
    }

    /**
     * Формирует клавиатуру для активных заявок: список заявок до выбора и набор действий после выбора.
     *
     * @param requests       перечень заявок, требующих внимания
     * @param selected       текущая выбранная заявка
     * @param navigationPath путь навигации к текущему экрану
     * @return готовая инлайн-клавиатура
     */
    private InlineKeyboardMarkup buildActiveRequestsKeyboard(List<ActionRequiredReturnRequestDto> requests,
                                                             ActionRequiredReturnRequestDto selected,
                                                             List<BuyerBotScreen> navigationPath) {
        List<InlineKeyboardRow> rows = new ArrayList<>();
        boolean hasRequests = requests != null && !requests.isEmpty();

        if (hasRequests && selected == null) {
            for (ActionRequiredReturnRequestDto request : requests) {
                if (request == null || request.requestId() == null || request.parcelId() == null) {
                    continue;
                }
                InlineKeyboardButton button = InlineKeyboardButton.builder()
                        .text(buildRequestSelectionLabel(request, null))
                        .callbackData(CALLBACK_RETURNS_ACTIVE_SELECT_PREFIX + request.requestId() + ':' + request.parcelId())
                        .build();
                rows.add(new InlineKeyboardRow(button));
            }
        } else if (selected != null) {
            rows.add(buildBackToListRow());
        }

        if (selected != null && selected.requestId() != null && selected.parcelId() != null) {
            rows.addAll(buildActionButtons(selected));
        }

        appendNavigationRow(rows, navigationPath);
        return InlineKeyboardMarkup.builder()
                .keyboard(rows)
                .build();
    }

    private String buildRequestSelectionLabel(ActionRequiredReturnRequestDto request,
                                              ActionRequiredReturnRequestDto selected) {
        String label = formatTrackNumber(request.trackNumber()) + " • " + resolveStoreLabel(request.storeName());
        if (selected != null && request.requestId() != null && request.requestId().equals(selected.requestId())) {
            return "▶ " + label;
        }
        return label;
    }

    private List<InlineKeyboardRow> buildActionButtons(ActionRequiredReturnRequestDto request) {
        List<InlineKeyboardRow> rows = new ArrayList<>();
        Long requestId = request.requestId();
        Long parcelId = request.parcelId();
        if (requestId == null || parcelId == null) {
            return rows;
        }
        rows.add(new InlineKeyboardRow(InlineKeyboardButton.builder()
                .text(BUTTON_RETURNS_ACTION_TRACK)
                .callbackData(CALLBACK_RETURNS_ACTIVE_TRACK_PREFIX + requestId + ':' + parcelId)
                .build()));
        rows.add(new InlineKeyboardRow(InlineKeyboardButton.builder()
                .text(BUTTON_RETURNS_ACTION_COMMENT)
                .callbackData(CALLBACK_RETURNS_ACTIVE_COMMENT_PREFIX + requestId + ':' + parcelId)
                .build()));
        if (request.status() == OrderReturnRequestStatus.EXCHANGE_APPROVED) {
            rows.add(new InlineKeyboardRow(InlineKeyboardButton.builder()
                    .text(BUTTON_RETURNS_ACTION_CANCEL_EXCHANGE)
                    .callbackData(CALLBACK_RETURNS_ACTIVE_CANCEL_EXCHANGE_PREFIX + requestId + ':' + parcelId)
                    .build()));
            rows.add(new InlineKeyboardRow(InlineKeyboardButton.builder()
                    .text(BUTTON_RETURNS_ACTION_CONVERT)
                    .callbackData(CALLBACK_RETURNS_ACTIVE_CONVERT_PREFIX + requestId + ':' + parcelId)
                    .build()));
        } else {
            rows.add(new InlineKeyboardRow(InlineKeyboardButton.builder()
                    .text(BUTTON_RETURNS_ACTION_CANCEL_RETURN)
                    .callbackData(CALLBACK_RETURNS_ACTIVE_CANCEL_PREFIX + requestId + ':' + parcelId)
                    .build()));
        }
        return rows;
    }

    /**
     * Создаёт строку с кнопкой возврата к списку заявок после выбора конкретной заявки.
     *
     * @return строка клавиатуры с кнопкой возврата
     */
    private InlineKeyboardRow buildBackToListRow() {
        InlineKeyboardButton backButton = InlineKeyboardButton.builder()
                .text(BUTTON_RETURNS_BACK_TO_LIST)
                .callbackData(CALLBACK_RETURNS_ACTIVE_BACK_TO_LIST)
                .build();
        return new InlineKeyboardRow(backButton);
    }
    /**
     * Возвращает отображаемое название магазина для таблицы активных заявок.
     *
     * @param rawStore исходное название магазина
     * @return приведённое название или заглушка
     */
    private String resolveStoreLabel(String rawStore) {
        if (rawStore == null || rawStore.isBlank()) {
            return "Магазин не указан";
        }
        return rawStore;
    }

    /**
     * Возвращает статус заявки или значение по умолчанию.
     *
     * @param status исходный статус
     * @return текст статуса для отображения
     */
    private String safeRequestStatus(String status) {
        if (status == null || status.isBlank()) {
            return OrderReturnRequestStatus.REGISTERED.getDisplayName();
        }
        return status;
    }

    /**
     * Возвращает строку или заменяет её дефисом при отсутствии значения.
     *
     * @param value исходное значение
     * @return строка для таблицы
     */
    private String safeOrDash(String value) {
        if (value == null || value.isBlank()) {
            return "—";
        }
        return value;
    }

    /**
     * Отображает этап выбора типа заявки при создании нового обращения.
     *
     * @param chatId идентификатор чата Telegram
     */
    private void showReturnRequestTypeScreen(Long chatId) {
        List<BuyerBotScreen> navigationPath = computeNavigationPath(chatId, BuyerBotScreen.RETURNS_CREATE_TYPE);
        InlineKeyboardMarkup markup = buildReturnRequestTypeKeyboard(navigationPath);
        String text = buildReturnRequestTypeText();
        sendInlineMessage(chatId, text, markup, BuyerBotScreen.RETURNS_CREATE_TYPE, navigationPath);
    }

    /**
     * Обрабатывает выбор типа заявки и переводит пользователя к выбору магазина.
     */
    private void handleReturnRequestTypeSelection(Long chatId,
                                                 CallbackQuery callbackQuery,
                                                 ReturnRequestType type) {
        answerCallbackQuery(callbackQuery, RETURNS_CREATE_TYPE_ACK);
        ChatSession session = ensureChatSession(chatId);
        session.clearReturnRequestData();
        session.setReturnRequestType(type);
        List<TelegramParcelInfoDTO> availableParcels = loadReturnableParcels(chatId);
        if (availableParcels.isEmpty()) {
            session.setState(BuyerChatState.AWAITING_REQUEST_TYPE);
            chatSessionRepository.save(session);
            transitionToState(chatId, BuyerChatState.AWAITING_REQUEST_TYPE);
            List<BuyerBotScreen> navigationPath = computeNavigationPath(chatId, BuyerBotScreen.RETURNS_CREATE_TYPE);
            InlineKeyboardMarkup navigationMarkup = buildBackAndMenuKeyboard();
            String text = escapeMarkdown(RETURNS_CREATE_NO_PARCELS);
            sendInlineMessage(chatId, text, navigationMarkup, BuyerBotScreen.RETURNS_CREATE_TYPE, navigationPath);
            return;
        }
        session.setState(BuyerChatState.AWAITING_STORE_SELECTION);
        chatSessionRepository.save(session);
        transitionToState(chatId, BuyerChatState.AWAITING_STORE_SELECTION);
        showReturnRequestStoreScreen(chatId, type, availableParcels);
    }

    /**
     * Показывает список магазинов, где есть посылки без активных заявок.
     */
    private void showReturnRequestStoreScreen(Long chatId,
                                              ReturnRequestType type,
                                              List<TelegramParcelInfoDTO> availableParcels) {
        Map<String, List<TelegramParcelInfoDTO>> grouped = groupReturnableParcelsByStore(availableParcels);
        List<BuyerBotScreen> navigationPath = computeNavigationPath(chatId, BuyerBotScreen.RETURNS_CREATE_STORE);

        if (grouped.isEmpty()) {
            ChatSession session = ensureChatSession(chatId);
            session.setState(BuyerChatState.AWAITING_STORE_SELECTION);
            chatSessionRepository.save(session);
            transitionToState(chatId, BuyerChatState.AWAITING_STORE_SELECTION);
            InlineKeyboardMarkup navigationMarkup = buildBackAndMenuKeyboard();
            String text = escapeMarkdown(RETURNS_CREATE_NO_PARCELS);
            sendInlineMessage(chatId, text, navigationMarkup, BuyerBotScreen.RETURNS_CREATE_STORE, navigationPath);
            return;
        }

        InlineKeyboardMarkup markup = buildReturnRequestStoreKeyboard(grouped, navigationPath);
        String text = buildReturnRequestStoreText(type);
        sendInlineMessage(chatId, text, markup, BuyerBotScreen.RETURNS_CREATE_STORE, navigationPath);
    }

    /**
     * Обрабатывает выбор магазина и предлагает список посылок этого магазина.
     */
    private void handleReturnRequestStoreSelection(Long chatId,
                                                   CallbackQuery callbackQuery,
                                                   String data) {
        Optional<String> decodedStore = decodeStoreFromCallback(data, CALLBACK_RETURNS_CREATE_STORE_PREFIX);
        if (decodedStore.isEmpty()) {
            answerCallbackQuery(callbackQuery, RETURNS_ACTIVE_ACTION_NOT_AVAILABLE);
            return;
        }

        ChatSession session = ensureChatSession(chatId);
        ReturnRequestType type = session.getReturnRequestType();
        if (type == null) {
            showReturnRequestTypeScreen(chatId);
            return;
        }

        List<TelegramParcelInfoDTO> availableParcels = loadReturnableParcels(chatId);
        Map<String, List<TelegramParcelInfoDTO>> grouped = groupReturnableParcelsByStore(availableParcels);
        List<TelegramParcelInfoDTO> storeParcels = grouped.getOrDefault(decodedStore.get(), List.of());
        if (storeParcels.isEmpty()) {
            answerCallbackQuery(callbackQuery, RETURNS_ACTIVE_ACTION_NOT_AVAILABLE);
            showReturnRequestStoreScreen(chatId, type, availableParcels);
            return;
        }

        answerCallbackQuery(callbackQuery, RETURNS_CREATE_STORE_ACK);
        session.setReturnStoreName(decodedStore.get());
        session.setState(BuyerChatState.AWAITING_PARCEL_SELECTION);
        chatSessionRepository.save(session);
        transitionToState(chatId, BuyerChatState.AWAITING_PARCEL_SELECTION);
        showReturnRequestParcelScreen(chatId, decodedStore.get(), storeParcels);
    }

    /**
     * Показывает список посылок выбранного магазина для оформления заявки.
     */
    private void showReturnRequestParcelScreen(Long chatId,
                                               String storeName,
                                               List<TelegramParcelInfoDTO> parcels) {
        List<BuyerBotScreen> navigationPath = computeNavigationPath(chatId, BuyerBotScreen.RETURNS_CREATE_REQUEST);
        InlineKeyboardMarkup markup = buildReturnRequestParcelKeyboard(parcels, navigationPath);
        String text = buildReturnRequestParcelText(storeName);
        sendInlineMessage(chatId, text, markup, BuyerBotScreen.RETURNS_CREATE_REQUEST, navigationPath);
    }

    /**
     * Обрабатывает выбор посылки и запускает соответствующую ветку сценария.
     */
    private void handleReturnRequestParcelSelection(Long chatId,
                                                    CallbackQuery callbackQuery,
                                                    String data) {
        Optional<Long> parcelIdOptional = parseIdFromCallback(data, CALLBACK_RETURNS_CREATE_PARCEL_PREFIX);
        if (parcelIdOptional.isEmpty()) {
            answerCallbackQuery(callbackQuery, RETURNS_ACTIVE_ACTION_NOT_AVAILABLE);
            return;
        }

        ChatSession session = ensureChatSession(chatId);
        ReturnRequestType type = session.getReturnRequestType();
        if (type == null) {
            showReturnRequestTypeScreen(chatId);
            return;
        }

        Long parcelId = parcelIdOptional.get();
        Optional<TelegramParcelInfoDTO> parcelOptional = findParcelById(chatId, parcelId);
        if (parcelOptional.isEmpty()) {
            answerCallbackQuery(callbackQuery, RETURNS_ACTIVE_ACTION_NOT_AVAILABLE);
            showReturnRequestTypeScreen(chatId);
            return;
        }

        TelegramParcelInfoDTO parcel = parcelOptional.get();
        if (parcel.hasActiveReturnRequest()) {
            answerCallbackQuery(callbackQuery, RETURNS_ACTIVE_ACTION_NOT_AVAILABLE);
            notifyReturnAlreadyRegistered(chatId, parcel);
            showReturnRequestTypeScreen(chatId);
            return;
        }

        answerCallbackQuery(callbackQuery, RETURNS_CREATE_PARCEL_ACK);
        if (type == ReturnRequestType.RETURN) {
            startReturnScenario(chatId, callbackQuery, parcel, session);
        } else {
            startExchangeScenario(chatId, callbackQuery, parcel, session);
        }
    }

    /**
     * Обрабатывает выбор причины возврата из инлайн-клавиатуры.
     *
     * @param chatId        идентификатор чата Telegram
     * @param callbackQuery исходный callback-запрос
     * @param data          данные callback с выбранной причиной
     */
    private void handleReturnReasonCallback(Long chatId,
                                            CallbackQuery callbackQuery,
                                            String data) {
        if (CALLBACK_NAVIGATE_BACK.equals(data)) {
            handleNavigateBack(chatId, callbackQuery);
            return;
        }
        if (CALLBACK_BACK_TO_MENU.equals(data)) {
            handleCallbackBackToMenu(chatId, callbackQuery, true);
            return;
        }

        Optional<String> reasonOptional = decodeReturnReason(data);
        if (reasonOptional.isEmpty()) {
            answerCallbackQuery(callbackQuery, RETURNS_ACTIVE_ACTION_NOT_AVAILABLE);
            return;
        }

        answerCallbackQuery(callbackQuery, PARCEL_RETURN_REASON_SELECTED_ACK);
        MaybeInaccessibleMessage message = callbackQuery.getMessage();
        Integer messageId = message != null ? message.getMessageId() : null;
        if (messageId != null) {
            removeInlineKeyboard(chatId, messageId);
        }
        handleReturnReason(chatId, reasonOptional.get());
    }

    /**
     * Обрабатывает выбор причины обмена из инлайн-клавиатуры.
     *
     * @param chatId        идентификатор чата Telegram
     * @param callbackQuery исходный callback-запрос
     * @param data          данные callback с выбранной причиной
     */
    private void handleExchangeReasonCallback(Long chatId,
                                              CallbackQuery callbackQuery,
                                              String data) {
        if (CALLBACK_NAVIGATE_BACK.equals(data)) {
            handleNavigateBack(chatId, callbackQuery);
            return;
        }
        if (CALLBACK_BACK_TO_MENU.equals(data)) {
            handleCallbackBackToMenu(chatId, callbackQuery, true);
            return;
        }

        Optional<String> reasonOptional = decodeReturnReason(data);
        if (reasonOptional.isEmpty()) {
            answerCallbackQuery(callbackQuery, RETURNS_ACTIVE_ACTION_NOT_AVAILABLE);
            return;
        }

        answerCallbackQuery(callbackQuery, PARCEL_RETURN_REASON_SELECTED_ACK);
        MaybeInaccessibleMessage message = callbackQuery.getMessage();
        Integer messageId = message != null ? message.getMessageId() : null;
        if (messageId != null) {
            removeInlineKeyboard(chatId, messageId);
        }
        handleExchangeReason(chatId, reasonOptional.get());
    }

    /**
     * Загружает доставленные посылки без активных заявок для оформления обращения.
     */
    private List<TelegramParcelInfoDTO> loadReturnableParcels(Long chatId) {
        return telegramService.getParcelsOverview(chatId)
                .map(TelegramParcelsOverviewDTO::getDelivered)
                .orElse(List.of())
                .stream()
                .filter(parcel -> parcel != null && !parcel.hasActiveReturnRequest())
                .collect(Collectors.toList());
    }

    /**
     * Группирует посылки по магазинам, сохраняя порядок появления.
     */
    private Map<String, List<TelegramParcelInfoDTO>> groupReturnableParcelsByStore(List<TelegramParcelInfoDTO> parcels) {
        Map<String, List<TelegramParcelInfoDTO>> grouped = new LinkedHashMap<>();
        if (parcels == null) {
            return grouped;
        }
        for (TelegramParcelInfoDTO parcel : parcels) {
            if (parcel == null) {
                continue;
            }
            String storeName = resolveStoreName(parcel);
            grouped.computeIfAbsent(storeName, key -> new ArrayList<>()).add(parcel);
        }
        return grouped;
    }

    /**
     * Формирует клавиатуру выбора типа заявки.
     */
    private InlineKeyboardMarkup buildReturnRequestTypeKeyboard(List<BuyerBotScreen> navigationPath) {
        InlineKeyboardButton returnButton = InlineKeyboardButton.builder()
                .text(RETURNS_CREATE_TYPE_RETURN_LABEL)
                .callbackData(CALLBACK_RETURNS_CREATE_TYPE_RETURN)
                .build();
        InlineKeyboardButton exchangeButton = InlineKeyboardButton.builder()
                .text(RETURNS_CREATE_TYPE_EXCHANGE_LABEL)
                .callbackData(CALLBACK_RETURNS_CREATE_TYPE_EXCHANGE)
                .build();

        List<InlineKeyboardRow> rows = new ArrayList<>();
        rows.add(new InlineKeyboardRow(returnButton, exchangeButton));
        appendNavigationRow(rows, navigationPath);
        return InlineKeyboardMarkup.builder()
                .keyboard(rows)
                .build();
    }

    /**
     * Формирует клавиатуру выбора магазина.
     */
    private InlineKeyboardMarkup buildReturnRequestStoreKeyboard(Map<String, List<TelegramParcelInfoDTO>> grouped,
                                                                List<BuyerBotScreen> navigationPath) {
        List<InlineKeyboardRow> rows = new ArrayList<>();
        for (Map.Entry<String, List<TelegramParcelInfoDTO>> entry : grouped.entrySet()) {
            String storeName = entry.getKey();
            int count = entry.getValue() != null ? entry.getValue().size() : 0;
            InlineKeyboardButton button = InlineKeyboardButton.builder()
                    .text(storeName + " (" + count + ")")
                    .callbackData(CALLBACK_RETURNS_CREATE_STORE_PREFIX + encodeStoreKey(storeName))
                    .build();
            rows.add(new InlineKeyboardRow(button));
        }
        appendNavigationRow(rows, navigationPath);
        return InlineKeyboardMarkup.builder()
                .keyboard(rows)
                .build();
    }

    /**
     * Формирует клавиатуру выбора посылки внутри магазина.
     */
    private InlineKeyboardMarkup buildReturnRequestParcelKeyboard(List<TelegramParcelInfoDTO> parcels,
                                                                 List<BuyerBotScreen> navigationPath) {
        List<InlineKeyboardRow> rows = new ArrayList<>();
        if (parcels != null) {
            for (TelegramParcelInfoDTO parcel : parcels) {
                if (parcel == null || parcel.getParcelId() == null) {
                    continue;
                }
                String track = formatTrackNumber(parcel.getTrackNumber());
                InlineKeyboardButton button = InlineKeyboardButton.builder()
                        .text(track)
                        .callbackData(CALLBACK_RETURNS_CREATE_PARCEL_PREFIX + parcel.getParcelId())
                        .build();
                rows.add(new InlineKeyboardRow(button));
            }
        }
        appendNavigationRow(rows, navigationPath);
        return InlineKeyboardMarkup.builder()
                .keyboard(rows)
                .build();
    }

    /**
     * Формирует клавиатуру с предустановленными причинами возврата.
     *
     * @return инлайн-клавиатура с кнопками причин
     */
    private InlineKeyboardMarkup buildReturnReasonKeyboard() {
        InlineKeyboardRow firstRow = new InlineKeyboardRow(
                InlineKeyboardButton.builder()
                        .text(RETURN_REASON_LABEL_NOT_FIT)
                        .callbackData(CALLBACK_RETURNS_REASON_NOT_FIT)
                        .build(),
                InlineKeyboardButton.builder()
                        .text(RETURN_REASON_LABEL_DEFECT)
                        .callbackData(CALLBACK_RETURNS_REASON_DEFECT)
                        .build()
        );
        InlineKeyboardRow secondRow = new InlineKeyboardRow(
                InlineKeyboardButton.builder()
                        .text(RETURN_REASON_LABEL_DISLIKE)
                        .callbackData(CALLBACK_RETURNS_REASON_DISLIKE)
                        .build(),
                InlineKeyboardButton.builder()
                        .text(RETURN_REASON_LABEL_OTHER)
                        .callbackData(CALLBACK_RETURNS_REASON_OTHER)
                        .build()
        );
        InlineKeyboardRow navigationRow = buildBackAndMenuRow();
        return InlineKeyboardMarkup.builder()
                .keyboard(List.of(firstRow, secondRow, navigationRow))
                .build();
    }

    /**
     * Формирует текст подтверждения успешной регистрации возврата.
     *
     * @param parcelLabel отображаемое обозначение посылки
     * @param reason      причина возврата
     * @param dateText    текстовое представление даты обращения
     * @return готовый текст для итогового сообщения
     */
    private String buildReturnCompletionText(String parcelLabel, String reason, String dateText) {
        String safeParcel = (parcelLabel == null || parcelLabel.isBlank())
                ? PARCEL_RETURN_NO_TRACK
                : parcelLabel;
        String safeReason = (reason == null || reason.isBlank())
                ? PARCEL_RETURN_REASON_UNKNOWN
                : reason;
        String safeDate = (dateText == null || dateText.isBlank())
                ? PARCEL_RETURN_DATE_UNKNOWN
                : dateText;

        StringBuilder builder = new StringBuilder();
        builder.append(escapeMarkdown("✅ Зафиксировали запрос на возврат посылки "))
                .append(escapeMarkdown(safeParcel))
                .append(escapeMarkdown("."))
                .append('\n');
        builder.append(escapeMarkdown("• Причина: "))
                .append(escapeMarkdown(safeReason))
                .append('\n');
        builder.append(escapeMarkdown("• Дата обращения: "))
                .append(escapeMarkdown(safeDate))
                .append('\n');
        builder.append(escapeMarkdown("ℹ️ Если трек появится позже, добавьте его через раздел «📂 Текущие заявки»."));
        return builder.toString();
    }

    /**
     * Строит клавиатуру финального экрана с подтверждением регистрации заявки.
     *
     * @return инлайн-клавиатура с кнопками возврата в меню и перехода к заявкам
     */
    private InlineKeyboardMarkup buildReturnCompletionKeyboard() {
        InlineKeyboardButton doneButton = InlineKeyboardButton.builder()
                .text(BUTTON_RETURNS_DONE)
                .callbackData(CALLBACK_RETURNS_DONE)
                .build();
        InlineKeyboardButton activeButton = InlineKeyboardButton.builder()
                .text(BUTTON_RETURNS_ACTIVE)
                .callbackData(CALLBACK_RETURNS_SHOW_ACTIVE)
                .build();
        InlineKeyboardRow mainRow = new InlineKeyboardRow(doneButton, activeButton);
        return InlineKeyboardMarkup.builder()
                .keyboard(List.of(mainRow))
                .build();
    }

    /**
     * Формирует текст подтверждения успешной регистрации обмена.
     *
     * @param parcelLabel отображаемое обозначение посылки
     * @param reason      причина обмена
     * @param dateText    текстовое представление даты обращения
     * @return готовый текст для итогового сообщения
     */
    private String buildExchangeCompletionText(String parcelLabel, String reason, String dateText) {
        String safeParcel = (parcelLabel == null || parcelLabel.isBlank())
                ? PARCEL_RETURN_NO_TRACK
                : parcelLabel;
        String safeReason = (reason == null || reason.isBlank())
                ? PARCEL_RETURN_REASON_UNKNOWN
                : reason;
        String safeDate = (dateText == null || dateText.isBlank())
                ? PARCEL_RETURN_DATE_UNKNOWN
                : dateText;

        StringBuilder builder = new StringBuilder();
        builder.append(escapeMarkdown("✅ Зафиксировали запрос на обмен по посылке "))
                .append(escapeMarkdown(safeParcel))
                .append(escapeMarkdown("."))
                .append('\n');
        builder.append(escapeMarkdown("• Причина: "))
                .append(escapeMarkdown(safeReason))
                .append('\n');
        builder.append(escapeMarkdown("• Дата обращения: "))
                .append(escapeMarkdown(safeDate))
                .append('\n');
        builder.append(escapeMarkdown("ℹ️ Менеджер свяжется с вами для уточнения деталей."));
        return builder.toString();
    }

    /**
     * Формирует клавиатуру финального экрана обмена с подтверждающими действиями.
     *
     * @return инлайн-клавиатура с кнопкой подтверждения и перехода к активным заявкам
     */
    private InlineKeyboardMarkup buildExchangeCompletionKeyboard() {
        InlineKeyboardButton okButton = InlineKeyboardButton.builder()
                .text(BUTTON_OUTCOME_OK)
                .callbackData(CALLBACK_RETURNS_DONE)
                .build();
        InlineKeyboardButton activeButton = InlineKeyboardButton.builder()
                .text(BUTTON_RETURNS_ACTIVE)
                .callbackData(CALLBACK_RETURNS_SHOW_ACTIVE)
                .build();
        InlineKeyboardRow mainRow = new InlineKeyboardRow(okButton, activeButton);
        return InlineKeyboardMarkup.builder()
                .keyboard(List.of(mainRow))
                .build();
    }

    /**
     * Формирует текст этапа выбора типа заявки.
     */
    private String buildReturnRequestTypeText() {
        return escapeMarkdown(RETURNS_CREATE_TITLE)
                + "\n\n"
                + escapeMarkdown(RETURNS_CREATE_TYPE_PROMPT);
    }

    /**
     * Формирует текст подсказки для выбора магазина.
     */
    private String buildReturnRequestStoreText(ReturnRequestType type) {
        String typeLabel = type == ReturnRequestType.RETURN
                ? RETURNS_CREATE_TYPE_RETURN_LABEL
                : RETURNS_CREATE_TYPE_EXCHANGE_LABEL;
        return escapeMarkdown(RETURNS_CREATE_TITLE)
                + "\n\n"
                + escapeMarkdown(String.format(RETURNS_CREATE_STORE_PROMPT, typeLabel));
    }

    /**
     * Формирует текст подсказки для выбора посылки в выбранном магазине.
     */
    private String buildReturnRequestParcelText(String storeName) {
        String storeLabel = storeName == null || storeName.isBlank() ? "Магазин не указан" : storeName;
        return escapeMarkdown(RETURNS_CREATE_TITLE)
                + "\n\n"
                + escapeMarkdown(String.format(RETURNS_CREATE_PARCEL_PROMPT, storeLabel));
    }

    /**
     * Кодирует название магазина для безопасного использования в callback-данных.
     */
    private String encodeStoreKey(String storeName) {
        String value = storeName == null ? "" : storeName;
        return STORE_KEY_ENCODER.encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Декодирует название магазина из callback-данных.
     */
    private Optional<String> decodeStoreFromCallback(String data, String prefix) {
        if (data == null || !data.startsWith(prefix)) {
            return Optional.empty();
        }
        String encoded = data.substring(prefix.length());
        if (encoded.isBlank()) {
            return Optional.of("Магазин не указан");
        }
        try {
            return Optional.of(new String(STORE_KEY_DECODER.decode(encoded), StandardCharsets.UTF_8));
        } catch (IllegalArgumentException ex) {
            log.warn("⚠️ Не удалось декодировать магазин из callback: {}", data, ex);
            return Optional.empty();
        }
    }

    /**
     * Преобразует callback-данные в текст причины возврата.
     *
     * @param data исходная строка callback
     * @return выбранная причина, если значение распознано
     */
    private Optional<String> decodeReturnReason(String data) {
        if (CALLBACK_RETURNS_REASON_NOT_FIT.equals(data)) {
            return Optional.of(RETURN_REASON_LABEL_NOT_FIT);
        }
        if (CALLBACK_RETURNS_REASON_DEFECT.equals(data)) {
            return Optional.of(RETURN_REASON_LABEL_DEFECT);
        }
        if (CALLBACK_RETURNS_REASON_DISLIKE.equals(data)) {
            return Optional.of(RETURN_REASON_LABEL_DISLIKE);
        }
        if (CALLBACK_RETURNS_REASON_OTHER.equals(data)) {
            return Optional.of(RETURN_REASON_LABEL_OTHER);
        }
        return Optional.empty();
    }

    /**
     * Извлекает числовой идентификатор из callback-строки.
     */
    private Optional<Long> parseIdFromCallback(String data, String prefix) {
        if (data == null || !data.startsWith(prefix)) {
            return Optional.empty();
        }
        String idPart = data.substring(prefix.length());
        if (idPart.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(Long.parseLong(idPart));
        } catch (NumberFormatException ex) {
            log.warn("⚠️ Некорректный идентификатор в callback: {}", data);
            return Optional.empty();
        }
    }

    private void handleActiveRequestSelection(Long chatId, CallbackQuery callbackQuery, String data) {
        Optional<RequestActionContext> contextOptional = parseActionContext(data, CALLBACK_RETURNS_ACTIVE_SELECT_PREFIX);
        if (contextOptional.isEmpty()) {
            answerCallbackQuery(callbackQuery, RETURNS_ACTIVE_ACTION_NOT_AVAILABLE);
            return;
        }
        RequestActionContext context = contextOptional.get();
        answerCallbackQuery(callbackQuery, "Заявка выбрана");
        ChatSession session = ensureChatSession(chatId);
        session.setActiveReturnRequestContext(context.requestId(), context.parcelId());
        chatSessionRepository.save(session);
        sendActiveReturnRequestsScreen(chatId);
    }

    /**
     * Очищает выбранную заявку и возвращает пользователя к списку активных заявок.
     *
     * @param chatId        идентификатор чата Telegram
     * @param callbackQuery исходный callback-запрос
     */
    private void handleActiveRequestBackToList(Long chatId, CallbackQuery callbackQuery) {
        answerCallbackQuery(callbackQuery, "Возвращаемся к списку");
        ChatSession session = ensureChatSession(chatId);
        session.clearActiveReturnRequestContext();
        chatSessionRepository.save(session);
        sendActiveReturnRequestsScreen(chatId);
    }

    private void handleActiveRequestTrack(Long chatId, CallbackQuery callbackQuery, String data) {
        Optional<RequestActionContext> contextOptional = parseActionContext(data, CALLBACK_RETURNS_ACTIVE_TRACK_PREFIX);
        if (contextOptional.isEmpty()) {
            answerCallbackQuery(callbackQuery, RETURNS_ACTIVE_ACTION_NOT_AVAILABLE);
            return;
        }
        RequestActionContext context = contextOptional.get();
        answerCallbackQuery(callbackQuery, "Ждём трек");
        ChatSession session = ensureChatSession(chatId);
        session.setActiveReturnRequestContext(context.requestId(), context.parcelId(), ReturnRequestEditMode.TRACK);
        session.setState(BuyerChatState.AWAITING_TRACK_UPDATE);
        chatSessionRepository.save(session);
        transitionToState(chatId, BuyerChatState.AWAITING_TRACK_UPDATE);
        sendSimpleMessage(chatId, RETURNS_ACTIVE_TRACK_PROMPT);
    }

    private void handleActiveRequestComment(Long chatId, CallbackQuery callbackQuery, String data) {
        Optional<RequestActionContext> contextOptional = parseActionContext(data, CALLBACK_RETURNS_ACTIVE_COMMENT_PREFIX);
        if (contextOptional.isEmpty()) {
            answerCallbackQuery(callbackQuery, RETURNS_ACTIVE_ACTION_NOT_AVAILABLE);
            return;
        }
        RequestActionContext context = contextOptional.get();
        answerCallbackQuery(callbackQuery, "Ждём комментарий");
        ChatSession session = ensureChatSession(chatId);
        session.setActiveReturnRequestContext(context.requestId(), context.parcelId(), ReturnRequestEditMode.COMMENT);
        session.setState(BuyerChatState.AWAITING_COMMENT_UPDATE);
        chatSessionRepository.save(session);
        transitionToState(chatId, BuyerChatState.AWAITING_COMMENT_UPDATE);
        sendSimpleMessage(chatId, RETURNS_ACTIVE_COMMENT_PROMPT);
    }

    private void handleActiveRequestCancelReturn(Long chatId, CallbackQuery callbackQuery, String data) {
        Optional<RequestActionContext> contextOptional = parseActionContext(data, CALLBACK_RETURNS_ACTIVE_CANCEL_PREFIX);
        if (contextOptional.isEmpty()) {
            answerCallbackQuery(callbackQuery, RETURNS_ACTIVE_ACTION_NOT_AVAILABLE);
            return;
        }
        RequestActionContext context = contextOptional.get();
        answerCallbackQuery(callbackQuery, "Отменяем возврат");
        ChatSession session = ensureChatSession(chatId);
        try {
            telegramService.closeReturnRequestFromTelegram(chatId, context.parcelId(), context.requestId());
            finalizeRequestUpdate(chatId, session, RETURNS_ACTIVE_CANCEL_RETURN_SUCCESS, BUTTON_OUTCOME_OK);
        } catch (AccessDeniedException ex) {
            log.warn("⚠️ Попытка отменить чужую заявку {} в чате {}", context.requestId(), chatId);
            finalizeRequestUpdate(chatId, session, PARCEL_RETURN_ACCESS_DENIED, BUTTON_OUTCOME_BACK);
        } catch (IllegalArgumentException | IllegalStateException ex) {
            log.warn("⚠️ Ошибка отмены возврата {}: {}", context.requestId(), ex.getMessage());
            finalizeRequestUpdate(chatId, session, RETURNS_ACTIVE_ACTION_FAILED, BUTTON_OUTCOME_BACK);
        } catch (Exception ex) {
            log.error("❌ Не удалось отменить возврат {}", context.requestId(), ex);
            finalizeRequestUpdate(chatId, session, RETURNS_ACTIVE_ACTION_FAILED, BUTTON_OUTCOME_BACK);
        }
    }

    private void handleActiveRequestCancelExchange(Long chatId, CallbackQuery callbackQuery, String data) {
        Optional<RequestActionContext> contextOptional = parseActionContext(data, CALLBACK_RETURNS_ACTIVE_CANCEL_EXCHANGE_PREFIX);
        if (contextOptional.isEmpty()) {
            answerCallbackQuery(callbackQuery, RETURNS_ACTIVE_ACTION_NOT_AVAILABLE);
            return;
        }
        RequestActionContext context = contextOptional.get();
        answerCallbackQuery(callbackQuery, "Отменяем обмен");
        ChatSession session = ensureChatSession(chatId);
        try {
            telegramService.cancelExchangeFromTelegram(chatId, context.parcelId(), context.requestId());
            finalizeRequestUpdate(chatId, session, RETURNS_ACTIVE_CANCEL_EXCHANGE_SUCCESS, BUTTON_OUTCOME_OK);
        } catch (AccessDeniedException ex) {
            log.warn("⚠️ Попытка отменить чужой обмен {} в чате {}", context.requestId(), chatId);
            finalizeRequestUpdate(chatId, session, PARCEL_RETURN_ACCESS_DENIED, BUTTON_OUTCOME_BACK);
        } catch (IllegalArgumentException | IllegalStateException ex) {
            log.warn("⚠️ Ошибка отмены обмена {}: {}", context.requestId(), ex.getMessage());
            finalizeRequestUpdate(chatId, session, RETURNS_ACTIVE_ACTION_FAILED, BUTTON_OUTCOME_BACK);
        } catch (Exception ex) {
            log.error("❌ Не удалось отменить обмен {}", context.requestId(), ex);
            finalizeRequestUpdate(chatId, session, RETURNS_ACTIVE_ACTION_FAILED, BUTTON_OUTCOME_BACK);
        }
    }

    private void handleActiveRequestConvert(Long chatId, CallbackQuery callbackQuery, String data) {
        Optional<RequestActionContext> contextOptional = parseActionContext(data, CALLBACK_RETURNS_ACTIVE_CONVERT_PREFIX);
        if (contextOptional.isEmpty()) {
            answerCallbackQuery(callbackQuery, RETURNS_ACTIVE_ACTION_NOT_AVAILABLE);
            return;
        }
        RequestActionContext context = contextOptional.get();
        answerCallbackQuery(callbackQuery, "Переводим в возврат");
        ChatSession session = ensureChatSession(chatId);
        try {
            telegramService.convertExchangeToReturnFromTelegram(chatId, context.parcelId(), context.requestId());
            finalizeRequestUpdate(chatId, session, RETURNS_ACTIVE_CONVERT_SUCCESS, BUTTON_OUTCOME_OK);
        } catch (AccessDeniedException ex) {
            log.warn("⚠️ Попытка изменить чужой обмен {} в чате {}", context.requestId(), chatId);
            finalizeRequestUpdate(chatId, session, PARCEL_RETURN_ACCESS_DENIED, BUTTON_OUTCOME_BACK);
        } catch (IllegalArgumentException | IllegalStateException ex) {
            log.warn("⚠️ Ошибка преобразования обмена {}: {}", context.requestId(), ex.getMessage());
            finalizeRequestUpdate(chatId, session, RETURNS_ACTIVE_ACTION_FAILED, BUTTON_OUTCOME_BACK);
        } catch (Exception ex) {
            log.error("❌ Не удалось перевести обмен {} в возврат", context.requestId(), ex);
            finalizeRequestUpdate(chatId, session, RETURNS_ACTIVE_ACTION_FAILED, BUTTON_OUTCOME_BACK);
        }
    }

    private ActionRequiredReturnRequestDto findRequestInfo(Long chatId, Long requestId) {
        return telegramService.getReturnRequestsRequiringAction(chatId).stream()
                .filter(dto -> requestId.equals(dto.requestId()))
                .findFirst()
                .orElse(null);
    }

    private void handleTrackUpdateInput(Long chatId, String text) {
        ChatSession session = ensureChatSession(chatId);
        Long requestId = session.getActiveReturnRequestId();
        Long parcelId = session.getActiveReturnParcelId();
        ReturnRequestEditMode mode = session.getReturnRequestEditMode();
        if (requestId == null || parcelId == null || mode == null) {
            sendSimpleMessage(chatId, RETURNS_ACTIVE_ACTION_NOT_AVAILABLE);
            session.clearActiveReturnRequestContext();
            session.setState(BuyerChatState.IDLE);
            chatSessionRepository.save(session);
            transitionToState(chatId, BuyerChatState.IDLE);
            sendActiveReturnRequestsScreen(chatId);
            return;
        }

        ActionRequiredReturnRequestDto requestInfo = findRequestInfo(chatId, requestId);
        if (requestInfo == null) {
            sendSimpleMessage(chatId, RETURNS_ACTIVE_ACTION_NOT_AVAILABLE);
            session.clearActiveReturnRequestContext();
            session.setState(BuyerChatState.IDLE);
            chatSessionRepository.save(session);
            sendActiveReturnRequestsScreen(chatId);
            return;
        }

        if (mode == ReturnRequestEditMode.TRACK) {
            processTrackUpdate(chatId, session, requestInfo, parcelId, requestId, text);
        } else {
            processCommentUpdate(chatId, session, requestInfo, parcelId, requestId, text);
        }
    }

    private void processTrackUpdate(Long chatId,
                                    ChatSession session,
                                    ActionRequiredReturnRequestDto requestInfo,
                                    Long parcelId,
                                    Long requestId,
                                    String text) {
        String normalized = text == null ? "" : text.strip();
        if (normalized.isEmpty()) {
            sendActiveRequestOutcomeMessage(chatId, RETURNS_ACTIVE_UPDATE_INVALID_TRACK, BUTTON_OUTCOME_BACK);
            return;
        }
        String newTrack = isSkipWord(normalized) ? null : normalized;
        String comment = requestInfo.comment();
        try {
            telegramService.updateReturnRequestDetailsFromTelegram(chatId, parcelId, requestId, newTrack, comment);
            finalizeRequestUpdate(chatId, session, RETURNS_ACTIVE_TRACK_SAVED, BUTTON_OUTCOME_OK);
        } catch (AccessDeniedException ex) {
            log.warn("⚠️ Попытка обновить чужую заявку {} в чате {}", requestId, chatId);
            finalizeRequestUpdate(chatId, session, PARCEL_RETURN_ACCESS_DENIED, BUTTON_OUTCOME_BACK);
        } catch (IllegalArgumentException ex) {
            log.warn("⚠️ Некорректный трек для заявки {}: {}", requestId, ex.getMessage());
            sendActiveRequestOutcomeMessage(chatId, RETURNS_ACTIVE_UPDATE_INVALID_TRACK, BUTTON_OUTCOME_BACK);
        } catch (IllegalStateException ex) {
            log.warn("⚠️ Заявку {} нельзя обновить: {}", requestId, ex.getMessage());
            finalizeRequestUpdate(chatId, session, RETURNS_ACTIVE_ACTION_FAILED, BUTTON_OUTCOME_BACK);
        } catch (Exception ex) {
            log.error("❌ Ошибка обновления заявки {}", requestId, ex);
            finalizeRequestUpdate(chatId, session, RETURNS_ACTIVE_UPDATE_FAILED, BUTTON_OUTCOME_BACK);
        }
    }

    private void processCommentUpdate(Long chatId,
                                      ChatSession session,
                                      ActionRequiredReturnRequestDto requestInfo,
                                      Long parcelId,
                                      Long requestId,
                                      String text) {
        String normalized = text == null ? "" : text.strip();
        if (normalized.isEmpty()) {
            sendActiveRequestOutcomeMessage(chatId, RETURNS_ACTIVE_COMMENT_INVALID, BUTTON_OUTCOME_BACK);
            return;
        }
        String newComment = isSkipWord(normalized) ? null : normalized;
        String reverseTrack = requestInfo.reverseTrackNumber();
        try {
            telegramService.updateReturnRequestDetailsFromTelegram(chatId, parcelId, requestId, reverseTrack, newComment);
            finalizeRequestUpdate(chatId, session, RETURNS_ACTIVE_COMMENT_SAVED, BUTTON_OUTCOME_OK);
        } catch (AccessDeniedException ex) {
            log.warn("⚠️ Попытка обновить чужую заявку {} в чате {}", requestId, chatId);
            finalizeRequestUpdate(chatId, session, PARCEL_RETURN_ACCESS_DENIED, BUTTON_OUTCOME_BACK);
        } catch (IllegalArgumentException ex) {
            log.warn("⚠️ Некорректный комментарий для заявки {}: {}", requestId, ex.getMessage());
            sendActiveRequestOutcomeMessage(chatId, RETURNS_ACTIVE_COMMENT_INVALID, BUTTON_OUTCOME_BACK);
        } catch (IllegalStateException ex) {
            log.warn("⚠️ Заявку {} нельзя обновить: {}", requestId, ex.getMessage());
            finalizeRequestUpdate(chatId, session, RETURNS_ACTIVE_ACTION_FAILED, BUTTON_OUTCOME_BACK);
        } catch (Exception ex) {
            log.error("❌ Ошибка обновления заявки {}", requestId, ex);
            finalizeRequestUpdate(chatId, session, RETURNS_ACTIVE_UPDATE_FAILED, BUTTON_OUTCOME_BACK);
        }
    }

    /**
     * Завершает редактирование активной заявки, очищая контекст и показывая итоговое сообщение.
     *
     * @param chatId     идентификатор чата Telegram
     * @param session    сохранённая сессия пользователя
     * @param message    текст уведомления о результате операции
     * @param buttonLabel подпись для кнопки возврата к списку заявок
     */
    private void finalizeRequestUpdate(Long chatId, ChatSession session, String message, String buttonLabel) {
        session = session != null ? session : ensureChatSession(chatId);
        resetActiveRequestContext(session);
        sendActiveRequestOutcomeMessage(chatId, message, buttonLabel);
    }

    /**
     * Сбрасывает выбор заявки и переводит чат в состояние ожидания новых действий.
     *
     * @param session актуальная сессия пользователя
     */
    private void resetActiveRequestContext(ChatSession session) {
        if (session == null) {
            return;
        }
        session.clearActiveReturnRequestContext();
        session.setState(BuyerChatState.IDLE);
        chatSessionRepository.save(session);
    }

    /**
     * Показывает результат операции над активной заявкой с кнопкой возврата к списку.
     *
     * @param chatId      идентификатор чата Telegram
     * @param text        сообщение для пользователя
     * @param buttonLabel подпись кнопки подтверждения результата
     */
    private void sendActiveRequestOutcomeMessage(Long chatId, String text, String buttonLabel) {
        if (chatId == null) {
            return;
        }
        String safeText = escapeMarkdown(text == null ? "" : text);
        String safeButtonLabel = (buttonLabel == null || buttonLabel.isBlank()) ? BUTTON_OUTCOME_BACK : buttonLabel;
        InlineKeyboardButton button = InlineKeyboardButton.builder()
                .text(safeButtonLabel)
                .callbackData(CALLBACK_RETURNS_ACTIVE_BACK_TO_LIST)
                .build();
        InlineKeyboardRow row = new InlineKeyboardRow(button);
        InlineKeyboardMarkup markup = InlineKeyboardMarkup.builder()
                .keyboard(List.of(row))
                .build();
        List<BuyerBotScreen> navigationPath = computeNavigationPath(chatId, BuyerBotScreen.RETURNS_ACTIVE_REQUESTS);
        sendInlineMessage(chatId, safeText, markup, BuyerBotScreen.RETURNS_ACTIVE_REQUESTS, navigationPath);
    }
    /**
     * Парсит идентификаторы заявки и посылки из callback-строки.
     */
    private Optional<RequestActionContext> parseActionContext(String data, String prefix) {
        if (data == null || !data.startsWith(prefix)) {
            return Optional.empty();
        }
        String[] parts = data.substring(prefix.length()).split(":");
        if (parts.length != 2) {
            return Optional.empty();
        }
        try {
            Long requestId = Long.parseLong(parts[0]);
            Long parcelId = Long.parseLong(parts[1]);
            return Optional.of(new RequestActionContext(requestId, parcelId));
        } catch (NumberFormatException ex) {
            log.warn("⚠️ Некорректные идентификаторы в callback: {}", data);
            return Optional.empty();
        }
    }

    /**
     * Контекст действия по заявке: идентификатор заявки и посылки.
     */
    private record RequestActionContext(Long requestId, Long parcelId) {
    }

    /**
     * Парсит текстовое сообщение пользователя с новым треком и комментарием.
     */
    /**
     * Унифицированный обработчик для отображения списков посылок выбранной категории.
     *
     * @param chatId         идентификатор чата Telegram
     * @param callbackQuery  исходный callback-запрос
     * @param acknowledgement текст подтверждения для всплывающего уведомления
     * @param title           заголовок сообщения со списком
     * @param extractor       функция получения нужной категории из сводки
     */
    private void handleParcelsCategoryCallback(Long chatId,
                                               CallbackQuery callbackQuery,
                                               String acknowledgement,
                                               String title,
                                               Function<TelegramParcelsOverviewDTO, List<TelegramParcelInfoDTO>> extractor,
                                               ParcelsSection section) {
        if (chatId == null) {
            answerCallbackQuery(callbackQuery, "Команда недоступна");
            return;
        }

        answerCallbackQuery(callbackQuery, acknowledgement);

        Optional<TelegramParcelsOverviewDTO> overviewOptional = telegramService.getParcelsOverview(chatId);

        List<BuyerBotScreen> navigationPath = computeNavigationPath(chatId, BuyerBotScreen.PARCELS, true);
        List<TelegramParcelInfoDTO> parcels = overviewOptional
                .map(extractor)
                .orElse(List.of());

        InlineKeyboardMarkup markup = section == ParcelsSection.DELIVERED
                ? buildDeliveredParcelsKeyboard(parcels, navigationPath)
                : buildNavigationKeyboard(navigationPath);

        String text = buildParcelsCategoryText(title, parcels, section);
        sendInlineMessage(chatId, text, markup, BuyerBotScreen.PARCELS, navigationPath);
    }

    /**
     * Формирует текст сообщения для выбранной категории посылок.
     *
     * @param title   заголовок раздела
     * @param parcels список посылок выбранной категории
     * @return готовый текст для отображения в чате
     */
    private String buildParcelsCategoryText(String title,
                                            List<TelegramParcelInfoDTO> parcels,
                                            ParcelsSection section) {
        StringBuilder builder = new StringBuilder();
        builder.append(escapeMarkdown(title)).append('\n').append('\n');
        if (parcels == null || parcels.isEmpty()) {
            builder.append(escapeMarkdown(NO_PARCELS_PLACEHOLDER));
            return builder.toString();
        }

        Map<String, List<TelegramParcelInfoDTO>> parcelsByStore = groupParcelsByStore(parcels);
        parcelsByStore.forEach((storeName, storeParcels) -> {
            builder.append('*').append(escapeMarkdown(storeName)).append('*').append('\n');
            for (TelegramParcelInfoDTO parcel : storeParcels) {
                builder.append("• ").append(formatParcelLine(parcel, section)).append('\n');
            }
            builder.append('\n');
        });

        return builder.toString().trim();
    }

    /**
     * Группирует посылки по магазинам, сохраняя порядок добавления для читабельности.
     *
     * @param parcels список посылок выбранной категории
     * @return отображение «магазин → посылки», упорядоченное по входному списку
     */
    private Map<String, List<TelegramParcelInfoDTO>> groupParcelsByStore(List<TelegramParcelInfoDTO> parcels) {
        Map<String, List<TelegramParcelInfoDTO>> grouped = new LinkedHashMap<>();
        for (TelegramParcelInfoDTO parcel : parcels) {
            String storeName = resolveStoreName(parcel);
            grouped.computeIfAbsent(storeName, key -> new ArrayList<>()).add(parcel);
        }
        return grouped;
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
     * Обрабатывает подтверждение просмотра административного объявления.
     *
     * @param chatId        идентификатор чата Telegram
     * @param callbackQuery исходный callback-запрос Telegram
     */
    private void handleAnnouncementAcknowledgement(Long chatId, CallbackQuery callbackQuery) {
        if (callbackQuery == null || chatId == null) {
            answerCallbackQuery(callbackQuery, "Команда недоступна");
            return;
        }

        ChatSession session = chatSessionRepository.find(chatId).orElse(null);
        if (session == null || session.getCurrentNotificationId() == null) {
            answerCallbackQuery(callbackQuery, "Уведомление недоступно");
            return;
        }

        if (session.isAnnouncementSeen()) {
            answerCallbackQuery(callbackQuery, "Уведомление уже закрыто");
            return;
        }

        chatSessionRepository.markAnnouncementSeen(chatId);
        answerCallbackQuery(callbackQuery, "Готово");
        sendMainMenu(chatId, true);
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
        List<BuyerBotScreen> navigationPath = computeNavigationPath(chatId, BuyerBotScreen.NAME_EDIT_PROMPT);
        InlineKeyboardMarkup markup = buildNavigationKeyboard(navigationPath);
        sendInlineMessage(chatId,
                escapeMarkdown(NAME_EDIT_ANCHOR_TEXT),
                markup,
                BuyerBotScreen.NAME_EDIT_PROMPT,
                navigationPath);
    }

    /**
     * Показывает главное меню и возвращает сценарий в состояние IDLE.
     * <p>
     * Метод очищает временный контекст возвратов и обменов, чтобы прекратить
     * дополнительные подсказки, и тем самым гарантирует корректное отображение меню.
     * </p>
     *
     * @param chatId идентификатор чата Telegram
     */
    private void handleMenuCommand(Long chatId) {
        resetReturnFlow(chatId);
        transitionToState(chatId, BuyerChatState.IDLE);
        Optional<Customer> optional = telegramService.findByChatId(chatId);
        if (optional.isPresent()) {
            Customer customer = optional.get();
            resetMenuAnchorIfAlreadyShown(chatId);
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
                SendMessage confirm = createPlainMessage(chatId,
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
            handleMenuCommand(chatId);
            return;
        }

        if ("/stats".equals(text) || BUTTON_STATS.equals(text) || "📊 Моя статистика".equals(text)) {
            sendStatisticsScreen(chatId);
            return;
        }

        if (BUTTON_PARCELS.equals(text)) {
            sendParcelsScreen(chatId);
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
        List<BuyerBotScreen> navigationPath = computeNavigationPath(chatId, BuyerBotScreen.STATISTICS);
        InlineKeyboardMarkup backMarkup = buildNavigationKeyboard(navigationPath);
        telegramService.getStatistics(chatId)
                .ifPresentOrElse(stats -> {
                    String text = buildStatisticsText(stats);
                    sendInlineMessage(chatId, text, backMarkup, BuyerBotScreen.STATISTICS, navigationPath);
                }, () -> sendInlineMessage(chatId,
                        buildStatisticsUnavailableText(),
                        backMarkup,
                        BuyerBotScreen.STATISTICS,
                        navigationPath));
    }

    /**
     * Собирает текст для раздела статистики, экранируя данные под формат MarkdownV2.
     *
     * @param stats агрегированные показатели покупателя
     * @return безопасный для отображения текст статистики
     */
    private String buildStatisticsText(CustomerStatisticsDTO stats) {
        if (stats == null) {
            return buildStatisticsUnavailableText();
        }

        String safePickedUp = escapeMarkdown(String.valueOf(stats.getPickedUpCount()));
        String safeReturned = escapeMarkdown(String.valueOf(stats.getReturnedCount()));
        String safeStores = stats.getStoreNames() == null || stats.getStoreNames().isEmpty()
                ? escapeMarkdown("-")
                : stats.getStoreNames().stream()
                .map(this::escapeMarkdown)
                .collect(Collectors.joining(", "));
        String safeReputation = stats.getReputation() == null
                ? escapeMarkdown("-")
                : escapeMarkdown(stats.getReputation().getDisplayName());

        StringBuilder builder = new StringBuilder();
        builder.append(escapeMarkdown("📊 Ваша статистика:")).append('\n');
        builder.append(escapeMarkdown("Забрано: ")).append(safePickedUp).append('\n');
        builder.append(escapeMarkdown("Не забрано: ")).append(safeReturned).append('\n');
        builder.append(escapeMarkdown("Магазины: ")).append(safeStores).append('\n');
        builder.append(escapeMarkdown("Репутация: ")).append(safeReputation);
        return builder.toString();
    }

    /**
     * Возвращает сообщение о недоступности статистики для отображения пользователю.
     *
     * @return текст ошибки, безопасный для MarkdownV2
     */
    private String buildStatisticsUnavailableText() {
        return escapeMarkdown("📊 Статистика пока недоступна. Попробуйте позже или проверьте, есть ли у вас активные заказы.");
    }

    /**
     * Отправляет раздел с посылками покупателя, разбитыми по статусам.
     *
     * @param chatId идентификатор чата Telegram
     */
    private void sendParcelsScreen(Long chatId) {
        List<BuyerBotScreen> navigationPath = computeNavigationPath(chatId, BuyerBotScreen.PARCELS);
        InlineKeyboardMarkup backMarkup = buildNavigationKeyboard(navigationPath);
        telegramService.getParcelsOverview(chatId)
                .ifPresentOrElse(overview -> {
                    boolean hasDelivered = hasParcels(overview.getDelivered());
                    boolean hasAwaiting = hasParcels(overview.getWaitingForPickup());
                    boolean hasTransit = hasParcels(overview.getInTransit());
                    boolean hasAny = hasDelivered || hasAwaiting || hasTransit;

                    InlineKeyboardMarkup markup = hasAny
                            ? buildParcelsOverviewKeyboard(overview, navigationPath)
                            : backMarkup;
                    String text = hasAny
                            ? buildParcelsScreenText()
                            : buildEmptyParcelsText();

                    sendInlineMessage(chatId, text, markup, BuyerBotScreen.PARCELS, navigationPath);
                }, () -> sendInlineMessage(chatId,
                        buildParcelsContactHintText(),
                        backMarkup,
                        BuyerBotScreen.PARCELS,
                        navigationPath));
    }

    /**
     * Формирует пояснение для случая, когда у покупателя нет посылок ни в одной категории.
     *
     * @return текст, уведомляющий об отсутствии посылок
     */
    private String buildEmptyParcelsText() {
        return escapeMarkdown("📦 Мои посылки") +
                "\n\n" +
                escapeMarkdown("Пока нет активных посылок");
    }

    /**
     * Формирует текстовую шапку раздела «Мои посылки», которую пользователь видит перед кнопками категорий.
     *
     * @return готовый текст для отправки в Telegram
     */
    private String buildParcelsScreenText() {
        return escapeMarkdown("📦 Мои посылки") +
                "\n\n" +
                escapeMarkdown("Выберите категорию:");
    }

    /**
     * Формирует текст подсказки о необходимости привязать номер телефона для раздела посылок.
     *
     * @return безопасный для MarkdownV2 текст уведомления
     */
    private String buildParcelsContactHintText() {
        return escapeMarkdown("📱 Привяжите номер телефона командой /start, чтобы видеть посылки в этом разделе.");
    }

    /**
     * Создаёт инлайн-клавиатуру со списком доступных категорий посылок и кнопкой возврата.
     *
     * @param overview сводка посылок по категориям
     * @return клавиатура с кнопками категорий
     */
    private InlineKeyboardMarkup buildParcelsOverviewKeyboard(TelegramParcelsOverviewDTO overview,
                                                              List<BuyerBotScreen> navigationPath) {
        int deliveredCount = Optional.ofNullable(overview.getDelivered())
                .map(List::size)
                .orElse(0);
        int awaitingCount = Optional.ofNullable(overview.getWaitingForPickup())
                .map(List::size)
                .orElse(0);
        int transitCount = Optional.ofNullable(overview.getInTransit())
                .map(List::size)
                .orElse(0);

        List<InlineKeyboardRow> rows = new ArrayList<>();
        if (hasParcels(overview.getDelivered())) {
            rows.add(new InlineKeyboardRow(buildParcelsCategoryButton(
                    buildParcelsCategoryLabel(BUTTON_PARCELS_DELIVERED, deliveredCount),
                    CALLBACK_PARCELS_DELIVERED)));
        }
        if (hasParcels(overview.getWaitingForPickup())) {
            rows.add(new InlineKeyboardRow(buildParcelsCategoryButton(
                    buildParcelsCategoryLabel(BUTTON_PARCELS_AWAITING, awaitingCount),
                    CALLBACK_PARCELS_AWAITING)));
        }
        if (hasParcels(overview.getInTransit())) {
            rows.add(new InlineKeyboardRow(buildParcelsCategoryButton(
                    buildParcelsCategoryLabel(BUTTON_PARCELS_TRANSIT, transitCount),
                    CALLBACK_PARCELS_TRANSIT)));
        }
        appendNavigationRow(rows, navigationPath);

        return InlineKeyboardMarkup.builder()
                .keyboard(rows)
                .build();
    }

    /**
     * Формирует подпись для кнопки категории посылок с отображением количества активных отправлений.
     *
     * @param baseLabel базовое название категории
     * @param count     количество посылок в категории
     * @return текст кнопки вида «базовое название (количество)»
     */
    private String buildParcelsCategoryLabel(String baseLabel, int count) {
        return String.format("%s (%d)", baseLabel, count);
    }

    /**
     * Проверяет, содержит ли категория посылок хотя бы один элемент.
     *
     * @param parcels список посылок категории
     * @return {@code true}, если список не пустой
     */
    private boolean hasParcels(List<TelegramParcelInfoDTO> parcels) {
        return parcels != null && !parcels.isEmpty();
    }

    /**
     * Создаёт кнопку выбора категории посылок.
     *
     * @param text         текст кнопки
     * @param callbackData callback-идентификатор категории
     * @return инлайн-кнопка для клавиатуры
     */
    private InlineKeyboardButton buildParcelsCategoryButton(String text, String callbackData) {
        return InlineKeyboardButton.builder()
                .text(text)
                .callbackData(callbackData)
                .build();
    }

    /**
     * Возвращает нормализованное название магазина для группировки и отображения.
     *
     * @param parcel DTO с информацией о посылке
     * @return название магазина или читаемая заглушка
     */
    private String resolveStoreName(TelegramParcelInfoDTO parcel) {
        if (parcel == null) {
            return "Магазин не указан";
        }
        String store = parcel.getStoreName();
        if (store == null || store.isBlank()) {
            return "Магазин не указан";
        }
        return store;
    }

    /**
     * Формирует строку посылки с учётом раздела и статуса для отображения пользователю.
     *
     * @param parcel  DTO с информацией о посылке
     * @param section раздел, в котором отображается посылка
     * @return строка с трек-номером и при необходимости предупреждением
     */
    private String formatParcelLine(TelegramParcelInfoDTO parcel, ParcelsSection section) {
        if (parcel == null) {
            return "—";
        }

        String track = escapeMarkdown(formatTrackNumber(parcel.getTrackNumber()));
        if (section == ParcelsSection.WAITING_FOR_PICKUP
                && parcel.getStatus() == GlobalStatus.CUSTOMER_NOT_PICKING_UP) {
            return String.format("%s — ⚠️ скоро уедет в магазин", track);
        }

        if (section == ParcelsSection.DELIVERED && parcel.hasActiveReturnRequest()) {
            return String.format("%s — %s", track, escapeMarkdown(PARCEL_ACTION_BLOCKED_TEXT));
        }

        return track;
    }

    /**
     * Строит клавиатуру для раздела «Полученные», содержащую только навигацию.
     *
     * @param parcels        список доставленных посылок (не используется, сохраняется для совместимости вызова)
     * @param navigationPath путь для отображения навигации
     * @return клавиатура с кнопкой(ами) навигации
     */
    private InlineKeyboardMarkup buildDeliveredParcelsKeyboard(List<TelegramParcelInfoDTO> parcels,
                                                               List<BuyerBotScreen> navigationPath) {
        List<InlineKeyboardRow> rows = new ArrayList<>();
        appendNavigationRow(rows, navigationPath);
        return InlineKeyboardMarkup.builder()
                .keyboard(rows)
                .build();
    }

    /**
     * Выполняет сценарий оформления возврата из Telegram.
     *
     * @param chatId        идентификатор чата Telegram
     * @param callbackQuery исходный callback-запрос
     * @param parcel        посылка, по которой запрошен возврат
     */
    private void startReturnScenario(Long chatId,
                                     CallbackQuery callbackQuery,
                                     TelegramParcelInfoDTO parcel,
                                     ChatSession session) {
        String track = formatTrackNumber(parcel.getTrackNumber());
        MaybeInaccessibleMessage callbackMessage = callbackQuery != null ? callbackQuery.getMessage() : null;
        Integer sourceMessageId = callbackMessage != null ? callbackMessage.getMessageId() : null;
        if (sourceMessageId != null) {
            removeInlineKeyboard(chatId, sourceMessageId);
        }
        session = session != null ? session : ensureChatSession(chatId);
        session.clearReturnRequestData();
        session.setReturnRequestType(ReturnRequestType.RETURN);
        session.setReturnParcelId(parcel.getParcelId());
        session.setReturnParcelTrackNumber(track);
        session.setReturnIdempotencyKey(UUID.randomUUID().toString());
        session.setState(BuyerChatState.AWAITING_RETURN_REASON);
        chatSessionRepository.save(session);
        transitionToState(chatId, BuyerChatState.AWAITING_RETURN_REASON);
        sendReturnReasonPrompt(chatId, track);
    }

    /**
     * Выполняет сценарий оформления обмена из Telegram.
     *
     * @param chatId        идентификатор чата Telegram
     * @param callbackQuery исходный callback-запрос
     * @param parcel        посылка, по которой запрошен обмен
     */
    private void startExchangeScenario(Long chatId,
                                       CallbackQuery callbackQuery,
                                       TelegramParcelInfoDTO parcel,
                                       ChatSession session) {
        String track = formatTrackNumber(parcel.getTrackNumber());
        MaybeInaccessibleMessage callbackMessage = callbackQuery != null ? callbackQuery.getMessage() : null;
        Integer sourceMessageId = callbackMessage != null ? callbackMessage.getMessageId() : null;
        if (sourceMessageId != null) {
            removeInlineKeyboard(chatId, sourceMessageId);
        }
        session = session != null ? session : ensureChatSession(chatId);
        session.clearReturnRequestData();
        session.setReturnRequestType(ReturnRequestType.EXCHANGE);
        session.setReturnParcelId(parcel.getParcelId());
        session.setReturnParcelTrackNumber(track);
        session.setReturnIdempotencyKey(UUID.randomUUID().toString());
        session.setState(BuyerChatState.AWAITING_EXCHANGE_REASON);
        chatSessionRepository.save(session);
        transitionToState(chatId, BuyerChatState.AWAITING_EXCHANGE_REASON);
        sendExchangeReasonPrompt(chatId, track);
    }

    /**
     * Отправляет пользователю клавиатуру с причинами возврата.
     *
     * @param chatId    идентификатор чата Telegram
     * @param trackLabel отображаемый трек-номер посылки
     */
    private void sendReturnReasonPrompt(Long chatId, String trackLabel) {
        if (chatId == null) {
            return;
        }
        String text = buildReturnReasonPromptText(trackLabel);
        InlineKeyboardMarkup markup = buildReturnReasonKeyboard();
        List<BuyerBotScreen> navigationPath = computeNavigationPath(chatId, BuyerBotScreen.RETURNS_RETURN_REASON);
        sendInlineMessage(chatId, text, markup, BuyerBotScreen.RETURNS_RETURN_REASON, navigationPath);
    }

    /**
     * Отправляет пользователю клавиатуру с причинами обмена.
     *
     * @param chatId    идентификатор чата Telegram
     * @param trackLabel отображаемый трек-номер посылки
     */
    private void sendExchangeReasonPrompt(Long chatId, String trackLabel) {
        if (chatId == null) {
            return;
        }
        String text = buildExchangeReasonPromptText(trackLabel);
        InlineKeyboardMarkup markup = buildReturnReasonKeyboard();
        List<BuyerBotScreen> navigationPath = computeNavigationPath(chatId, BuyerBotScreen.RETURNS_EXCHANGE_REASON);
        sendInlineMessage(chatId, text, markup, BuyerBotScreen.RETURNS_EXCHANGE_REASON, navigationPath);
    }

    /**
     * Формирует текст запроса причины возврата с экранированием MarkdownV2.
     *
     * @param trackLabel отображаемый трек-номер посылки
     * @return безопасный для MarkdownV2 текст подсказки
     */
    private String buildReturnReasonPromptText(String trackLabel) {
        String safeTrack = escapeMarkdown(trackLabel == null ? "" : trackLabel);
        return escapeMarkdown("📩 Начинаем оформление возврата по посылке ")
                + safeTrack
                + escapeMarkdown(". Выберите, пожалуйста, причину ниже.");
    }

    /**
     * Формирует текст запроса причины обмена с экранированием MarkdownV2.
     *
     * @param trackLabel отображаемый трек-номер посылки
     * @return безопасный для MarkdownV2 текст подсказки
     */
    private String buildExchangeReasonPromptText(String trackLabel) {
        String safeTrack = escapeMarkdown(trackLabel == null ? "" : trackLabel);
        return escapeMarkdown("📩 Начинаем оформление обмена по посылке ")
                + safeTrack
                + escapeMarkdown(". Выберите, пожалуйста, причину ниже.");
    }

    /**
     * Повторно показывает клавиатуру выбора причины возврата, используя данные текущего сеанса.
     * <p>
     * Метод применяется при восстановлении экрана после устаревшего callback, чтобы пользователь
     * увидел актуальное сообщение с кнопками выбора причины.
     * </p>
     *
     * @param chatId идентификатор чата Telegram
     */
    private void resendReturnReasonPrompt(Long chatId) {
        if (chatId == null) {
            return;
        }

        ChatSession session = ensureChatSession(chatId);
        Integer anchorMessageId = session.getAnchorMessageId();
        if (anchorMessageId != null) {
            removeInlineKeyboard(chatId, anchorMessageId);
        }
        String trackLabel = session.getReturnParcelTrackNumber();
        if (trackLabel == null || trackLabel.isBlank()) {
            sendMainMenu(chatId);
            return;
        }

        sendReturnReasonPrompt(chatId, trackLabel);
    }

    /**
     * Повторно показывает клавиатуру выбора причины обмена, восстанавливая якорный экран.
     *
     * @param chatId идентификатор чата Telegram
     */
    private void resendExchangeReasonPrompt(Long chatId) {
        if (chatId == null) {
            return;
        }

        ChatSession session = ensureChatSession(chatId);
        Integer anchorMessageId = session.getAnchorMessageId();
        if (anchorMessageId != null) {
            removeInlineKeyboard(chatId, anchorMessageId);
        }
        String trackLabel = session.getReturnParcelTrackNumber();
        if (trackLabel == null || trackLabel.isBlank()) {
            sendMainMenu(chatId);
            return;
        }

        sendExchangeReasonPrompt(chatId, trackLabel);
    }

    /**
     * Находит посылку в актуальной сводке по идентификатору.
     *
     * @param chatId   идентификатор чата Telegram
     * @param parcelId идентификатор посылки
     * @return DTO посылки, если она доступна в сводке
     */
    private Optional<TelegramParcelInfoDTO> findParcelById(Long chatId, Long parcelId) {
        if (chatId == null || parcelId == null) {
            return Optional.empty();
        }

        return telegramService.getParcelsOverview(chatId)
                .map(overview -> Stream.of(
                                overview.getDelivered(),
                                overview.getWaitingForPickup(),
                                overview.getInTransit())
                        .filter(Objects::nonNull)
                        .flatMap(List::stream)
                        .filter(parcel -> parcelId.equals(parcel.getParcelId()))
                        .findFirst()
                        .orElse(null))
                .filter(Objects::nonNull);
    }
    /**
     * Подготавливает трек-номер к отображению, заменяя пустые значения заглушкой.
     *
     * @param trackNumber исходный трек-номер
     * @return трек-номер или «Без номера» при отсутствии данных
     */
    private String formatTrackNumber(String trackNumber) {
        if (trackNumber == null || trackNumber.isBlank()) {
            return "Без номера";
        }
        return trackNumber;
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
        List<BuyerBotScreen> navigationPath = computeNavigationPath(chatId, BuyerBotScreen.SETTINGS);
        InlineKeyboardMarkup markup = buildSettingsKeyboard(customer, navigationPath);
        sendInlineMessage(chatId, text, markup, BuyerBotScreen.SETTINGS, navigationPath);
    }

    /**
     * Отправляет справочную информацию по работе с ботом.
     *
     * @param chatId идентификатор чата Telegram
     */
    private void sendHelpScreen(Long chatId) {
        String rawHelpText = """
                ❓ Помощь

                • /start — привязать чат и получать уведомления.
                • /menu — открыть главное меню.
                • /stats — показать статистику.

                Управляйте уведомлениями и ФИО через раздел "⚙️ Настройки".
                """.stripIndent();
        String helpText = escapeMarkdown(rawHelpText);
        List<BuyerBotScreen> navigationPath = computeNavigationPath(chatId, BuyerBotScreen.HELP);
        InlineKeyboardMarkup markup = buildNavigationKeyboard(navigationPath);
        sendInlineMessage(chatId, helpText, markup, BuyerBotScreen.HELP, navigationPath);
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
        handleCallbackBackToMenu(chatId, callbackQuery, false);
    }

    /**
     * Возвращает пользователя в главное меню из инлайн-режима.
     *
     * @param chatId        идентификатор чата Telegram
     * @param callbackQuery исходный callback-запрос
     * @param useRender     следует ли обновить экран через {@link #renderScreen(Long, BuyerBotScreen)}
     */
    private void handleCallbackBackToMenu(Long chatId,
                                          CallbackQuery callbackQuery,
                                          boolean useRender) {
        ChatSession session = chatSessionRepository.find(chatId).orElse(null);
        if (session != null) {
            resetReturnScenario(chatId, session);
        } else {
            transitionToState(chatId, BuyerChatState.IDLE);
        }
        answerCallbackQuery(callbackQuery, "Открыл меню");
        if (useRender) {
            renderScreen(chatId, BuyerBotScreen.MENU);
        } else {
            sendMainMenu(chatId);
        }
    }

    /**
     * Возвращает пользователя на предыдущий экран, сохраняя навигационную историю.
     *
     * @param chatId        идентификатор чата Telegram
     * @param callbackQuery исходный callback-запрос
     */
    private void handleNavigateBack(Long chatId, CallbackQuery callbackQuery) {
        if (chatId == null) {
            answerCallbackQuery(callbackQuery, "Команда недоступна");
            return;
        }

        ChatSession session = chatSessionRepository.find(chatId).orElse(null);
        if (session == null) {
            answerCallbackQuery(callbackQuery, "Возвращаю в меню");
            sendMainMenu(chatId);
            return;
        }

        BuyerBotScreen targetScreen = session.navigateBack();
        synchronizeStateAfterBackwardNavigation(chatId, session, targetScreen);
        answerCallbackQuery(callbackQuery, "Назад");
        renderScreen(chatId, targetScreen);
    }

    /**
     * Синхронизирует сценарное состояние при возврате по кнопке «Назад».
     *
     * @param chatId       идентификатор чата Telegram
     * @param session      сессия пользователя, хранящая данные сценария
     * @param targetScreen экран, на который требуется вернуться
     */
    private void synchronizeStateAfterBackwardNavigation(Long chatId,
                                                         ChatSession session,
                                                         BuyerBotScreen targetScreen) {
        if (session == null) {
            transitionToState(chatId, BuyerChatState.IDLE);
            return;
        }

        if (targetScreen == BuyerBotScreen.RETURNS_CREATE_TYPE) {
            session.setReturnStoreName(null);
            session.setReturnParcelId(null);
            session.setReturnParcelTrackNumber(null);
        } else if (targetScreen == BuyerBotScreen.RETURNS_CREATE_STORE) {
            session.setReturnParcelId(null);
            session.setReturnParcelTrackNumber(null);
        }

        BuyerChatState targetState = resolveStateForScreen(targetScreen);
        session.setState(targetState);
        chatSessionRepository.save(session);
        transitionToState(chatId, targetState);
    }

    /**
     * Подбирает сценарное состояние, соответствующее экрану интерфейса.
     *
     * @param targetScreen экран, на который выполняется переход
     * @return состояние сценария, которое нужно установить
     */
    private BuyerChatState resolveStateForScreen(BuyerBotScreen targetScreen) {
        if (targetScreen == null) {
            return BuyerChatState.IDLE;
        }
        return switch (targetScreen) {
            case RETURNS_CREATE_TYPE -> BuyerChatState.AWAITING_REQUEST_TYPE;
            case RETURNS_CREATE_STORE -> BuyerChatState.AWAITING_STORE_SELECTION;
            case RETURNS_CREATE_REQUEST -> BuyerChatState.AWAITING_PARCEL_SELECTION;
            case RETURNS_ACTIVE_REQUESTS -> BuyerChatState.AWAITING_ACTIVE_REQUEST_SELECTION;
            case RETURNS_RETURN_REASON -> BuyerChatState.AWAITING_RETURN_REASON;
            case RETURNS_EXCHANGE_REASON -> BuyerChatState.AWAITING_EXCHANGE_REASON;
            default -> BuyerChatState.IDLE;
        };
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
        List<BuyerBotScreen> navigationPath = computeNavigationPath(chatId, BuyerBotScreen.SETTINGS);
        InlineKeyboardMarkup settingsKeyboard = buildSettingsKeyboard(customer, navigationPath);
        sendInlineMessage(chatId, settingsText, settingsKeyboard, BuyerBotScreen.SETTINGS, navigationPath);
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
            nameStatus = escapeMarkdown("не указано");
        } else if (customer.getNameSource() == NameSource.USER_CONFIRMED) {
            nameStatus = escapeMarkdown(fullName) + ' ' + escapeMarkdown("(подтверждено)");
        } else {
            nameStatus = escapeMarkdown(fullName) + ' ' + escapeMarkdown("(ожидает подтверждения)");
        }

        StringBuilder builder = new StringBuilder();
        builder.append(escapeMarkdown("⚙️ Настройки")).append('\n').append('\n');
        builder.append(escapeMarkdown("Уведомления: "))
                .append(escapeMarkdown(notificationsStatus))
                .append('\n');
        builder.append(escapeMarkdown("Имя: ")).append(nameStatus);
        if (awaitingNameInput) {
            builder.append("\n\n").append(escapeMarkdown("✍️ Ожидается ввод нового ФИО."));
        }
        return builder.toString();
    }

    /**
     * Создаёт инлайн-клавиатуру для раздела настроек.
     *
     * @param customer покупатель, для которого формируются кнопки
     * @return готовая инлайн-клавиатура
     */
    private InlineKeyboardMarkup buildSettingsKeyboard(Customer customer,
                                                       List<BuyerBotScreen> navigationPath) {
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

        appendNavigationRow(rows, navigationPath);

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
                .callbackData(CALLBACK_NAVIGATE_BACK)
                .build();
    }

    /**
     * Формирует кнопку быстрого перехода в главное меню.
     *
     * @return инлайн-кнопка «Меню»
     */
    private InlineKeyboardButton buildMenuButton() {
        return InlineKeyboardButton.builder()
                .text(BUTTON_MENU)
                .callbackData(CALLBACK_BACK_TO_MENU)
                .build();
    }

    /**
     * Формирует строку с кнопками «Назад» и «Меню» для навигации.
     *
     * @return строка инлайн-клавиатуры с навигационными кнопками
     */
    private InlineKeyboardRow buildBackAndMenuRow() {
        return new InlineKeyboardRow(buildBackButton(), buildMenuButton());
    }

    /**
     * Формирует клавиатуру с кнопками навигации «Назад» и «Меню».
     *
     * @return инлайн-клавиатура с двумя кнопками навигации
     */
    private InlineKeyboardMarkup buildBackAndMenuKeyboard() {
        InlineKeyboardRow navigationRow = buildBackAndMenuRow();
        return InlineKeyboardMarkup.builder()
                .keyboard(List.of(navigationRow))
                .build();
    }

    /**
     * Строит строку навигации с кнопками «Назад» и «Меню» при необходимости.
     *
     * @param navigationPath путь экранов, ведущий к текущему состоянию
     * @return строка с кнопками или {@code null}, если навигация не нужна
     */
    private InlineKeyboardRow buildNavigationRow(List<BuyerBotScreen> navigationPath) {
        if (navigationPath == null || navigationPath.size() <= 1) {
            return null;
        }
        List<InlineKeyboardButton> buttons = new ArrayList<>();
        buttons.add(buildBackButton());
        if (navigationPath.size() > 2) {
            buttons.add(buildMenuButton());
        }
        return new InlineKeyboardRow(buttons);
    }

    /**
     * Добавляет строку навигации в набор кнопок, если она требуется для текущего экрана.
     *
     * @param rows           коллекция строк клавиатуры
     * @param navigationPath путь экранов для расчёта навигации
     */
    private void appendNavigationRow(List<InlineKeyboardRow> rows, List<BuyerBotScreen> navigationPath) {
        InlineKeyboardRow navigationRow = buildNavigationRow(navigationPath);
        if (navigationRow != null) {
            rows.add(navigationRow);
        }
    }

    /**
     * Создаёт клавиатуру, содержащую только навигационные кнопки.
     *
     * @param navigationPath путь экранов, ведущий к текущему состоянию
     * @return готовая инлайн-клавиатура
     */
    private InlineKeyboardMarkup buildNavigationKeyboard(List<BuyerBotScreen> navigationPath) {
        InlineKeyboardRow navigationRow = buildNavigationRow(navigationPath);
        List<InlineKeyboardRow> rows = navigationRow == null
                ? new ArrayList<>()
                : new ArrayList<>(List.of(navigationRow));
        return InlineKeyboardMarkup.builder()
                .keyboard(rows)
                .build();
    }

    /**
     * Рассчитывает путь навигации после перехода на указанный экран.
     *
     * @param chatId идентификатор чата Telegram
     * @param screen экран, который нужно показать
     * @return последовательность экранов от корня до целевого состояния
     */
    private List<BuyerBotScreen> computeNavigationPath(Long chatId, BuyerBotScreen screen) {
        return computeNavigationPath(chatId, screen, false);
    }

    /**
     * Рассчитывает путь навигации с учётом необходимости добавить повтор текущего экрана.
     *
     * @param chatId         идентификатор чата Telegram
     * @param screen         экран, который нужно показать
     * @param allowDuplicate разрешено ли дублировать текущий экран в пути
     * @return последовательность экранов от корня до целевого состояния
     */
    private List<BuyerBotScreen> computeNavigationPath(Long chatId,
                                                       BuyerBotScreen screen,
                                                       boolean allowDuplicate) {
        if (chatId == null) {
            return List.of();
        }
        ChatSession session = chatSessionRepository.find(chatId)
                .orElse(new ChatSession(chatId, BuyerChatState.IDLE, null, null));
        return session.projectNavigationPath(screen, allowDuplicate);
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
     * Сохраняет выбранную причину возврата и завершает сценарий регистрации.
     *
     * @param chatId      идентификатор чата Telegram
     * @param reasonLabel текст причины, выбранной пользователем
     */
    private void handleReturnReason(Long chatId, String reasonLabel) {
        if (chatId == null) {
            return;
        }

        String normalized = reasonLabel == null ? "" : reasonLabel.strip();
        if (normalized.isEmpty()) {
            sendSimpleMessage(chatId, PARCEL_RETURN_REASON_REMINDER);
            return;
        }

        ChatSession session = ensureChatSession(chatId);
        if (!ensureReturnContext(chatId, session)) {
            return;
        }

        session.setReturnReason(normalized);
        chatSessionRepository.save(session);
        finalizeReturnFlow(chatId, session);
    }

    /**
     * Сохраняет выбранную причину обмена и завершает сценарий регистрации.
     *
     * @param chatId      идентификатор чата Telegram
     * @param reasonLabel текст причины, выбранной пользователем
     */
    private void handleExchangeReason(Long chatId, String reasonLabel) {
        if (chatId == null) {
            return;
        }

        String normalized = reasonLabel == null ? "" : reasonLabel.strip();
        if (normalized.isEmpty()) {
            sendSimpleMessage(chatId, PARCEL_EXCHANGE_REASON_REMINDER);
            return;
        }

        ChatSession session = ensureChatSession(chatId);
        if (!ensureReturnContext(chatId, session)) {
            return;
        }

        session.setReturnReason(normalized);
        chatSessionRepository.save(session);
        handleExchangeConfirmation(chatId);
    }

    /**
     * Обрабатывает подтверждение успешной регистрации возврата или обмена и возвращает пользователя в меню.
     *
     * @param chatId        идентификатор чата Telegram
     * @param callbackQuery исходный callback-запрос
     */
    private void handleReturnCompletionAcknowledgement(Long chatId, CallbackQuery callbackQuery) {
        answerCallbackQuery(callbackQuery, "Меню обновлено");
        ChatSession session = chatSessionRepository.find(chatId).orElse(null);
        if (session != null) {
            resetReturnScenario(chatId, session);
        } else {
            transitionToState(chatId, BuyerChatState.IDLE);
        }
        renderScreen(chatId, BuyerBotScreen.MENU);
    }

    /**
     * Обрабатывает подтверждение обмена и завершает сценарий.
     *
     * @param chatId идентификатор чата Telegram
     * @param text   ответ пользователя
     */
    private void handleExchangeConfirmation(Long chatId) {
        ChatSession session = ensureChatSession(chatId);
        if (!ensureReturnContext(chatId, session)) {
            return;
        }

        Long parcelId = session.getReturnParcelId();
        String parcelLabel = session.getReturnParcelTrackNumber() != null
                ? session.getReturnParcelTrackNumber()
                : "Без номера";
        String idempotencyKey = session.getReturnIdempotencyKey();
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            idempotencyKey = UUID.randomUUID().toString();
            session.setReturnIdempotencyKey(idempotencyKey);
        }

        ZonedDateTime requestedAt = ZonedDateTime.now(ZoneOffset.UTC);

        OrderReturnRequest request;
        try {
            request = telegramService.registerReturnRequestFromTelegram(
                    chatId,
                    parcelId,
                    idempotencyKey,
                    session.getReturnReason()
            );
            if (request == null) {
                log.warn("⚠️ Сервис возвратов вернул null при регистрации обмена по посылке {}", parcelId);
                sendSimpleMessage(chatId, PARCEL_EXCHANGE_FAILED);
                resetReturnScenario(chatId, session);
                return;
            }
            Long requestId = request.getId();
            if (requestId == null) {
                log.warn("⚠️ Регистрация обмена вернула заявку без идентификатора для посылки {}", parcelId);
                sendSimpleMessage(chatId, PARCEL_EXCHANGE_FAILED);
                resetReturnScenario(chatId, session);
                return;
            }
            telegramService.approveExchangeFromTelegram(chatId, parcelId, requestId);
        } catch (IllegalStateException ex) {
            log.warn("⚠️ Не удалось запустить обмен по посылке {}: {}", parcelId, ex.getMessage());
            String message = ex.getMessage();
            if (message != null && message.contains("активная заявка")) {
                notifyReturnAlreadyRegistered(chatId, parcelLabel);
            } else if (message != null && message.contains("Вручена")) {
                sendSimpleMessage(chatId, PARCEL_RETURN_STATUS_INVALID);
            } else {
                sendSimpleMessage(chatId, PARCEL_EXCHANGE_FAILED);
            }
            resetReturnScenario(chatId, session);
            return;
        } catch (IllegalArgumentException ex) {
            log.warn("⚠️ Некорректные данные обмена по посылке {}: {}", parcelId, ex.getMessage());
            String message = ex.getMessage();
            if (message != null && message.contains("другими данными")) {
                sendSimpleMessage(chatId, PARCEL_RETURN_IDEMPOTENCY_CONFLICT);
            } else {
                sendSimpleMessage(chatId, PARCEL_EXCHANGE_FAILED);
            }
            resetReturnScenario(chatId, session);
            return;
        } catch (AccessDeniedException ex) {
            log.warn("⚠️ Попытка обмена для чужой посылки {} в чате {}", parcelId, chatId);
            sendSimpleMessage(chatId, PARCEL_RETURN_ACCESS_DENIED);
            resetReturnScenario(chatId, session);
            return;
        } catch (Exception ex) {
            log.error("❌ Ошибка запуска обмена по посылке {}", parcelId, ex);
            sendSimpleMessage(chatId, PARCEL_EXCHANGE_FAILED);
            resetReturnScenario(chatId, session);
            return;
        }

        session.setState(BuyerChatState.IDLE);
        chatSessionRepository.save(session);
        transitionToState(chatId, BuyerChatState.IDLE);

        sendExchangeCompletionScreen(chatId, session, requestedAt);
    }

    /**
     * Возвращает сохранённую сессию или создаёт новую заготовку.
     *
     * @param chatId идентификатор чата Telegram
     * @return экземпляр сессии для модификации
     */
    private ChatSession ensureChatSession(Long chatId) {
        return chatSessionRepository.find(chatId)
                .orElseGet(() -> new ChatSession(chatId, BuyerChatState.IDLE, null, null));
    }

    /**
     * Проверяет, что сценарий возврата активен и содержит необходимый контекст.
     *
     * @param chatId  идентификатор чата Telegram
     * @param session сессия, загруженная из хранилища
     * @return {@code true}, если продолжение сценария возможно
     */
    private boolean ensureReturnContext(Long chatId, ChatSession session) {
        if (session == null || session.getReturnParcelId() == null) {
            if (session != null) {
                session.clearReturnRequestData();
                session.setState(BuyerChatState.IDLE);
                chatSessionRepository.save(session);
            }
            transitionToState(chatId, BuyerChatState.IDLE);
            sendSimpleMessage(chatId, PARCEL_RETURN_CONTEXT_LOST);
            return false;
        }
        return true;
    }

    /**
     * Проверяет, что значение соответствует словам пропуска.
     *
     * @param value исходный текст
     * @return {@code true}, если пользователь хочет пропустить шаг
     */
    private boolean isSkipWord(String value) {
        if (value == null) {
            return true;
        }
        String normalized = value.strip().toLowerCase();
        return normalized.isEmpty()
                || normalized.equals("нет")
                || normalized.equals("не")
                || normalized.equals("no")
                || normalized.equals("none")
                || normalized.equals("-");
    }

    /**
     * Завершает сценарий возврата, формируя итоговое сообщение и сбрасывая состояние.
     *
     * @param chatId  идентификатор чата Telegram
     * @param session активная сессия диалога
     */
    private void finalizeReturnFlow(Long chatId, ChatSession session) {
        if (session == null) {
            return;
        }

        Long parcelId = session.getReturnParcelId();
        String parcelLabel = session.getReturnParcelTrackNumber();
        ZonedDateTime requestedAt = ZonedDateTime.now(ZoneOffset.UTC);
        String idempotencyKey = session.getReturnIdempotencyKey();
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            idempotencyKey = UUID.randomUUID().toString();
            session.setReturnIdempotencyKey(idempotencyKey);
        }

        try {
            telegramService.registerReturnRequestFromTelegram(
                    chatId,
                    parcelId,
                    idempotencyKey,
                    session.getReturnReason()
            );
        } catch (IllegalStateException ex) {
            log.warn("⚠️ Не удалось зарегистрировать возврат по посылке {}: {}", parcelId, ex.getMessage());
            handleReturnRegistrationIllegalState(chatId, parcelLabel, ex);
            resetReturnScenario(chatId, session);
            return;
        } catch (IllegalArgumentException ex) {
            log.warn("⚠️ Некорректные данные возврата по посылке {}: {}", parcelId, ex.getMessage());
            handleReturnRegistrationIllegalArgument(chatId, ex);
            resetReturnScenario(chatId, session);
            return;
        } catch (AccessDeniedException ex) {
            log.warn("⚠️ Попытка оформить возврат для чужой посылки {} в чате {}", parcelId, chatId);
            sendSimpleMessage(chatId, PARCEL_RETURN_ACCESS_DENIED);
            resetReturnScenario(chatId, session);
            return;
        } catch (Exception ex) {
            log.error("❌ Ошибка регистрации возврата по посылке {}", parcelId, ex);
            sendSimpleMessage(chatId, PARCEL_RETURN_REGISTRATION_FAILED);
            resetReturnScenario(chatId, session);
            return;
        }

        session.setState(BuyerChatState.IDLE);
        chatSessionRepository.save(session);
        transitionToState(chatId, BuyerChatState.IDLE);

        sendReturnCompletionScreen(chatId, session, requestedAt);
    }

    /**
     * Перерисовывает экран подтверждения оформления возврата на основе сохранённых данных.
     *
     * @param chatId идентификатор чата Telegram
     */
    private void sendReturnCompletionScreen(Long chatId) {
        ChatSession session = ensureChatSession(chatId);
        if (session.getReturnParcelTrackNumber() == null && session.getReturnReason() == null) {
            sendMainMenu(chatId);
            return;
        }
        sendReturnCompletionScreen(chatId, session, null);
    }

    /**
     * Показывает экран подтверждения оформления возврата с итоговой сводкой.
     *
     * @param chatId      идентификатор чата Telegram
     * @param session     активная сессия, содержащая данные заявки
     * @param requestedAt дата регистрации заявки или {@code null}
     */
    private void sendReturnCompletionScreen(Long chatId, ChatSession session, ZonedDateTime requestedAt) {
        if (chatId == null) {
            return;
        }

        ChatSession activeSession = session != null ? session : ensureChatSession(chatId);
        String parcelLabel = activeSession.getReturnParcelTrackNumber();
        if (parcelLabel == null || parcelLabel.isBlank()) {
            parcelLabel = PARCEL_RETURN_NO_TRACK;
        }
        String reason = activeSession.getReturnReason();
        if (reason == null || reason.isBlank()) {
            reason = PARCEL_RETURN_REASON_UNKNOWN;
        }
        String dateText = requestedAt != null
                ? formatReturnDateForSummary(requestedAt)
                : PARCEL_RETURN_DATE_UNKNOWN;

        List<BuyerBotScreen> navigationPath = computeNavigationPath(chatId, BuyerBotScreen.RETURNS_RETURN_COMPLETION);
        String text = buildReturnCompletionText(parcelLabel, reason, dateText);
        InlineKeyboardMarkup markup = buildReturnCompletionKeyboard();
        sendInlineMessage(chatId, text, markup, BuyerBotScreen.RETURNS_RETURN_COMPLETION, navigationPath);
    }

    /**
     * Перерисовывает экран подтверждения оформления обмена на основе сохранённых данных.
     *
     * @param chatId идентификатор чата Telegram
     */
    private void sendExchangeCompletionScreen(Long chatId) {
        ChatSession session = ensureChatSession(chatId);
        if (session.getReturnParcelTrackNumber() == null && session.getReturnReason() == null) {
            sendMainMenu(chatId);
            return;
        }
        sendExchangeCompletionScreen(chatId, session, null);
    }

    /**
     * Показывает экран подтверждения оформления обмена с итоговой сводкой.
     *
     * @param chatId      идентификатор чата Telegram
     * @param session     активная сессия, содержащая данные заявки
     * @param requestedAt дата регистрации заявки или {@code null}
     */
    private void sendExchangeCompletionScreen(Long chatId, ChatSession session, ZonedDateTime requestedAt) {
        if (chatId == null) {
            return;
        }

        ChatSession activeSession = session != null ? session : ensureChatSession(chatId);
        String parcelLabel = activeSession.getReturnParcelTrackNumber();
        if (parcelLabel == null || parcelLabel.isBlank()) {
            parcelLabel = PARCEL_RETURN_NO_TRACK;
        }
        String reason = activeSession.getReturnReason();
        if (reason == null || reason.isBlank()) {
            reason = PARCEL_RETURN_REASON_UNKNOWN;
        }
        String dateText = requestedAt != null
                ? formatReturnDateForSummary(requestedAt)
                : PARCEL_RETURN_DATE_UNKNOWN;

        List<BuyerBotScreen> navigationPath = computeNavigationPath(chatId, BuyerBotScreen.RETURNS_EXCHANGE_COMPLETION);
        String text = buildExchangeCompletionText(parcelLabel, reason, dateText);
        InlineKeyboardMarkup markup = buildExchangeCompletionKeyboard();
        sendInlineMessage(chatId, text, markup, BuyerBotScreen.RETURNS_EXCHANGE_COMPLETION, navigationPath);
    }

    /**
     * Сообщает пользователю, что по выбранной посылке уже есть активная заявка.
     *
     * @param chatId идентификатор чата Telegram
     * @param parcel посылка, выбранная пользователем
     */
    private void notifyReturnAlreadyRegistered(Long chatId, TelegramParcelInfoDTO parcel) {
        if (parcel == null) {
            return;
        }
        notifyReturnAlreadyRegistered(chatId, parcel.getTrackNumber());
    }

    /**
     * Отправляет сообщение о том, что по посылке уже зарегистрирована активная заявка.
     */
    private void notifyReturnAlreadyRegistered(Long chatId, String trackLabel) {
        String track = formatTrackNumber(trackLabel);
        sendSimpleMessage(chatId, String.format(PARCEL_RETURN_ALREADY_REGISTERED_TEMPLATE, track));
    }

    /**
     * Обрабатывает бизнес-конфликты при регистрации возврата.
     */
    private void handleReturnRegistrationIllegalState(Long chatId,
                                                      String parcelLabel,
                                                      IllegalStateException ex) {
        String message = ex.getMessage();
        if (message != null && message.contains("активная заявка")) {
            notifyReturnAlreadyRegistered(chatId, parcelLabel);
            return;
        }
        if (message != null && message.contains("Вручена")) {
            sendSimpleMessage(chatId, PARCEL_RETURN_STATUS_INVALID);
            return;
        }
        sendSimpleMessage(chatId, PARCEL_RETURN_REGISTRATION_FAILED);
    }

    /**
     * Сообщает пользователю о проблемах с идемпотентным ключом или данными заявки.
     */
    private void handleReturnRegistrationIllegalArgument(Long chatId, IllegalArgumentException ex) {
        String message = ex.getMessage();
        if (message != null && message.contains("другими данными")) {
            sendSimpleMessage(chatId, PARCEL_RETURN_IDEMPOTENCY_CONFLICT);
            return;
        }
        sendSimpleMessage(chatId, PARCEL_RETURN_REGISTRATION_FAILED);
    }

    /**
     * Сбрасывает состояние сценария возврата после завершения или ошибки.
     */
    private void resetReturnScenario(Long chatId, ChatSession session) {
        if (session == null) {
            return;
        }
        session.clearReturnRequestData();
        session.setState(BuyerChatState.IDLE);
        chatSessionRepository.save(session);
        transitionToState(chatId, BuyerChatState.IDLE);
    }

    /**
     * Форматирует дату для итогового сообщения, учитывая возможное отсутствие значения.
     *
     * @param date сохранённая дата запроса возврата
     * @return строка для отображения пользователю
     */
    private String formatReturnDateForSummary(ZonedDateTime date) {
        if (date == null) {
            return PARCEL_RETURN_DATE_UNKNOWN;
        }
        return PARCEL_RETURN_DATE_FORMAT.format(date);
    }

    /**
     * Сообщает пользователю, что по выбранной посылке уже есть активная заявка.
     *
     * @param chatId идентификатор чата Telegram
     * @param parcel посылка, выбранная пользователем
     */
    /**
     * Сбрасывает временные данные сценария возврата при возврате в меню.
     *
     * @param chatId идентификатор чата Telegram
     */
    private void resetReturnFlow(Long chatId) {
        ChatSession session = chatSessionRepository.find(chatId).orElse(null);
        if (session == null) {
            return;
        }
        if (session.getReturnParcelId() == null
                && session.getReturnReason() == null
                && session.getReturnIdempotencyKey() == null) {
            return;
        }
        session.clearReturnRequestData();
        chatSessionRepository.save(session);
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
     * Напоминает пользователю, что выбор типа заявки выполняется через инлайн-кнопки.
     *
     * @param chatId идентификатор чата Telegram
     */
    private void remindRequestTypeSelection(Long chatId) {
        sendSimpleMessage(chatId, RETURNS_CREATE_REPEAT_HINT);
    }

    /**
     * Напоминает о необходимости выбрать магазин с помощью кнопок.
     *
     * @param chatId идентификатор чата Telegram
     */
    private void remindStoreSelection(Long chatId) {
        sendSimpleMessage(chatId, RETURNS_CREATE_REPEAT_HINT);
    }

    /**
     * Сообщает пользователю, что посылку следует выбрать из списка кнопок.
     *
     * @param chatId идентификатор чата Telegram
     */
    private void remindParcelSelection(Long chatId) {
        sendSimpleMessage(chatId, RETURNS_CREATE_REPEAT_HINT);
    }

    /**
     * Подсказывает, что действия по заявке доступны через кнопки под таблицей.
     *
     * @param chatId идентификатор чата Telegram
     */
    private void remindRequestAction(Long chatId) {
        sendSimpleMessage(chatId, RETURNS_ACTIVE_SELECT_PROMPT);
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
            chatSessionRepository.clearContactRequestSent(chatId);
            chatSessionRepository.markKeyboardHidden(chatId);
            return;
        }

        chatSessionRepository.clearContactRequestSent(chatId);
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
        sendMainMenu(chatId, null, false);
    }

    /**
     * Отправляет главное меню с основными разделами бота.
     * <p>Меню содержит кнопки статистики, настроек и помощи.</p>
     *
     * @param chatId                   идентификатор чата Telegram
     * @param forceResendOnNotModified требует ли сценарий повторной отправки при ошибке «message is not modified»
     */
    private void sendMainMenu(Long chatId, boolean forceResendOnNotModified) {
        sendMainMenu(chatId, null, forceResendOnNotModified);
    }

    /**
     * Отправляет главное меню, используя заранее загруженные данные о покупателе.
     *
     * @param chatId   идентификатор чата Telegram
     * @param customer покупатель, информация о котором уже загружена
     */
    private void sendMainMenu(Long chatId, Customer customer) {
        sendMainMenu(chatId, customer, false);
    }

    /**
     * Отправляет главное меню, переиспользуя загруженного покупателя и контролируя пересылку при отсутствии изменений.
     *
     * @param chatId                   идентификатор чата Telegram
     * @param customer                 покупатель, информация о котором уже загружена
     * @param forceResendOnNotModified требует ли сценарий повторной отправки при ответе «message is not modified»
     */
    private void sendMainMenu(Long chatId, Customer customer, boolean forceResendOnNotModified) {
        if (chatId == null) {
            return;
        }

        Customer resolvedCustomer = customer != null
                ? customer
                : telegramService.findByChatId(chatId).orElse(null);
        String text = buildMainMenuText(resolvedCustomer);
        InlineKeyboardMarkup markup = buildMainMenuKeyboard(resolvedCustomer);
        List<BuyerBotScreen> navigationPath = computeNavigationPath(chatId, BuyerBotScreen.MENU);
        sendInlineMessage(chatId, text, markup, BuyerBotScreen.MENU, forceResendOnNotModified, navigationPath);

        ensurePersistentKeyboard(chatId);
        if (resolvedCustomer != null && resolvedCustomer.isTelegramConfirmed()) {
            showActiveAnnouncement(chatId);
        }
    }

    /**
     * Отрисовывает активное объявление администратора для указанного подтверждённого чата.
     *
     * @param chatId идентификатор чата Telegram, в который следует отправить баннер
     */
    @Override
    public void showActiveAnnouncement(Long chatId) {
        renderActiveAnnouncement(chatId);
    }

    /**
     * Формирует текст якорного сообщения главного меню в зависимости от состояния покупателя.
     *
     * @param customer покупатель, для которого отображается меню
     * @return текст для отображения в сообщении меню
     */
    private String buildMainMenuText(Customer customer) {
        StringBuilder builder = new StringBuilder();
        builder.append(escapeMarkdown("📋 Главное меню")).append('\n').append('\n');

        if (customer == null) {
            builder.append(escapeMarkdown("Поделитесь номером телефона командой /start, чтобы получать уведомления и статистику."))
                    .append('\n')
                    .append('\n');
        } else {
            String notificationsStatus = customer.isNotificationsEnabled() ? "включены" : "отключены";
            builder.append(escapeMarkdown("Уведомления: "))
                    .append(escapeMarkdown(notificationsStatus))
                    .append('\n');

            String fullName = customer.getFullName();
            if (fullName == null || fullName.isBlank()) {
                builder.append(escapeMarkdown("Имя: не указано"));
            } else if (customer.getNameSource() == NameSource.USER_CONFIRMED) {
                builder.append(escapeMarkdown("Имя: "))
                        .append(escapeMarkdown(fullName))
                        .append(' ')
                        .append(escapeMarkdown("(подтверждено)"));
            } else {
                builder.append(escapeMarkdown("Имя: "))
                        .append(escapeMarkdown(fullName))
                        .append(' ')
                        .append(escapeMarkdown("(ожидает подтверждения)"));
            }
            builder.append('\n').append('\n');
        }

        builder.append(escapeMarkdown("Выберите раздел через кнопки ниже или воспользуйтесь клавишей «🏠 Меню» на клавиатуре."));
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
        InlineKeyboardButton parcelsButton = InlineKeyboardButton.builder()
                .text(BUTTON_PARCELS)
                .callbackData(CALLBACK_MENU_SHOW_PARCELS)
                .build();
        InlineKeyboardButton returnsButton = InlineKeyboardButton.builder()
                .text(BUTTON_RETURNS)
                .callbackData(CALLBACK_MENU_RETURNS_EXCHANGES)
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
        rows.add(new InlineKeyboardRow(statsButton, parcelsButton));
        rows.add(new InlineKeyboardRow(returnsButton, settingsButton));
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
     * Показывает актуальное объявление администратора для подтверждённого покупателя.
     * <p>
     * Баннер отправляется, если пользователь ещё не видел актуальную версию объявления.
     * При обновлении текста администратором метод сбрасывает признак просмотра и
     * переотправляет баннер, чтобы покупатель получил изменённую информацию.
     * </p>
     *
     * @param chatId идентификатор чата Telegram подтверждённого покупателя
     */
    private void renderActiveAnnouncement(Long chatId) {
        if (chatId == null) {
            return;
        }

        adminNotificationService.findActiveNotification()
                .ifPresent(notification -> {
                    ChatSession session = chatSessionRepository.find(chatId).orElse(null);
                    Long storedId = session != null ? session.getCurrentNotificationId() : null;
                    boolean seen = session != null && session.isAnnouncementSeen();
                    ZonedDateTime storedUpdatedAt = session != null ? session.getAnnouncementUpdatedAt() : null;

                    boolean matchesActive = storedId != null && notification.getId().equals(storedId);
                    boolean contentRefreshed = isNotificationRefreshed(notification.getUpdatedAt(), storedUpdatedAt);
                    if (!matchesActive || contentRefreshed) {
                        Integer anchorId = session != null ? session.getAnchorMessageId() : null;
                        chatSessionRepository.updateAnnouncement(chatId,
                                notification.getId(),
                                anchorId,
                                notification.getUpdatedAt());
                        matchesActive = true;
                        seen = false;
                    }

                    if (matchesActive && !seen) {
                        renderAnnouncementBanner(chatId, notification);
                    }
                });
    }

    /**
     * Фиксирует просмотр активного объявления сразу после привязки номера телефона.
     *
     * @param chatId идентификатор чата Telegram
     */
    private void markActiveAnnouncementSeen(Long chatId) {
        if (chatId == null) {
            return;
        }

        adminNotificationService.findActiveNotification()
                .ifPresent(notification -> {
                    Long notificationId = notification.getId();
                    if (notificationId == null) {
                        return;
                    }
                    chatSessionRepository.setAnnouncementAsSeen(chatId,
                            notificationId,
                            notification.getUpdatedAt());
                });
    }

    /**
     * Отрисовывает баннер объявления в якорном сообщении главного меню.
     *
     * @param chatId       идентификатор чата Telegram
     * @param notification активное объявление администратора
     */
    private void renderAnnouncementBanner(Long chatId, AdminNotification notification) {
        if (chatId == null || notification == null) {
            return;
        }

        String text = buildAnnouncementText(notification);
        InlineKeyboardMarkup markup = buildAnnouncementKeyboard();
        List<BuyerBotScreen> navigationPath = computeNavigationPath(chatId, BuyerBotScreen.MENU);
        sendInlineMessage(chatId, text, markup, BuyerBotScreen.MENU, true, navigationPath);

        Integer anchorId = chatSessionRepository.find(chatId)
                .map(ChatSession::getAnchorMessageId)
                .orElse(null);
        chatSessionRepository.updateAnnouncement(chatId,
                notification.getId(),
                anchorId,
                notification.getUpdatedAt());
    }

    /**
     * Определяет, было ли объявление обновлено после последнего сохранённого показа.
     *
     * @param notificationUpdatedAt фактическое время последнего обновления объявления
     * @param storedUpdatedAt       время обновления, сохранённое в сессии пользователя
     * @return {@code true}, если объявление обновилось и требуется сбросить состояние
     */
    private boolean isNotificationRefreshed(ZonedDateTime notificationUpdatedAt, ZonedDateTime storedUpdatedAt) {
        if (notificationUpdatedAt == null) {
            return false;
        }
        return storedUpdatedAt == null || notificationUpdatedAt.isAfter(storedUpdatedAt);
    }

    /**
     * Формирует текст баннера объявления с заголовком и пунктами списка.
     *
     * @param notification объявление, подготовленное администраторами
     * @return текстовое содержимое баннера
     */
    private String buildAnnouncementText(AdminNotification notification) {
        StringBuilder builder = new StringBuilder();
        builder.append("📣 ").append(escapeMarkdown(notification.getTitle())).append("\n\n");

        boolean hasBody = false;
        List<String> lines = notification.getBodyLines();
        if (lines != null) {
            for (String line : lines) {
                if (line == null || line.isBlank()) {
                    continue;
                }
                builder.append("• ").append(escapeMarkdown(line)).append('\n');
                hasBody = true;
            }
        }

        if (hasBody) {
            builder.append('\n');
        }
        builder.append(escapeMarkdown("Нажмите «Ок», чтобы вернуться в меню."));
        return builder.toString();
    }

    /**
     * Создаёт инлайн-клавиатуру для баннера объявления с единственной кнопкой подтверждения.
     *
     * @return клавиатура с кнопкой «Ок»
     */
    private InlineKeyboardMarkup buildAnnouncementKeyboard() {
        InlineKeyboardButton okButton = InlineKeyboardButton.builder()
                .text("Ок")
                .callbackData(CALLBACK_ANNOUNCEMENT_ACK)
                .build();
        return InlineKeyboardMarkup.builder()
                .keyboard(List.of(new InlineKeyboardRow(okButton)))
                .build();
    }

    /**
     * Формирует инлайн-клавиатуру для подтверждения или изменения имени.
     *
     * @return клавиатура с действиями по управлению именем
     */
    private InlineKeyboardMarkup buildNameConfirmationKeyboard(List<BuyerBotScreen> navigationPath) {
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
        appendNavigationRow(rows, navigationPath);

        return InlineKeyboardMarkup.builder()
                .keyboard(rows)
                .build();
    }

    /**
     * Создаёт постоянную reply-клавиатуру с кнопкой быстрого возврата в меню.
     *
     * @return разметка reply-клавиатуры
     */
    private ReplyKeyboardMarkup createPersistentMenuKeyboard() {
        KeyboardButton menuButton = new KeyboardButton(BUTTON_MENU);
        KeyboardRow row = new KeyboardRow(List.of(menuButton));

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
     * Сообщение, которое содержит клавиатуру, остаётся последним, чтобы кнопка
     * «🏠 Меню» была доступна даже после перезапуска бота и ручного скрытия клавиатуры
     * пользователем. Во время ожидания контакта клавиатура не восстанавливается,
     * чтобы не мешать сценарию отправки номера телефона.
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

        SendMessage message = createPlainMessage(chatId,
                "Клавиша быстрого доступа доступна на панели ниже: «🏠 Меню».");
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
                                   BuyerBotScreen screen,
                                   List<BuyerBotScreen> navigationPath) {
        sendInlineMessage(chatId, text, markup, screen, false, navigationPath);
    }

    /**
     * Показывает или обновляет якорное сообщение, сохраняя данные в устойчивом хранилище.
     *
     * @param chatId                   идентификатор чата Telegram
     * @param text                     текст, который необходимо отобразить
     * @param markup                   инлайн-клавиатура для сообщения
     * @param screen                   экран, который следует зафиксировать для последующего восстановления
     * @param forceResendOnNotModified требовать переотправку при ответе «message is not modified»
     */
    private void sendInlineMessage(Long chatId,
                                   String text,
                                   InlineKeyboardMarkup markup,
                                   BuyerBotScreen screen,
                                   boolean forceResendOnNotModified,
                                   List<BuyerBotScreen> navigationPath) {
        if (chatId == null) {
            return;
        }

        ChatSession session = chatSessionRepository.find(chatId).orElse(null);
        boolean manualAnchorReset = session != null
                && session.getLastScreen() == screen
                && session.getAnchorMessageId() == null;
        Integer messageId = session != null ? session.getAnchorMessageId() : null;

        boolean shouldSendNewMessage = manualAnchorReset || messageId == null;

        if (messageId != null && !manualAnchorReset) {
            EditMessageText edit = EditMessageText.builder()
                    .chatId(chatId.toString())
                    .messageId(messageId)
                    .text(text)
                    .parseMode(TELEGRAM_PARSE_MODE)
                    .replyMarkup(markup)
                    .build();
            try {
                telegramClient.execute(edit);
                chatSessionRepository.updateAnchorAndScreen(chatId, messageId, screen, navigationPath);
                return;
            } catch (TelegramApiException e) {
                String errorMessage = e.getMessage();
                boolean notModified = errorMessage != null && errorMessage.contains("message is not modified");
                if (notModified) {
                    retainAnchorAfterNotModified(chatId, messageId, screen, navigationPath);
                    if (forceResendOnNotModified) {
                        log.debug("ℹ️ Telegram сообщил об отсутствии изменений для чата {}, переиспользую текущее сообщение", chatId);
                    } else {
                        log.debug("ℹ️ Содержимое якорного сообщения для чата {} не изменилось, якорь обновлён без повторной отправки", chatId);
                    }
                    return;
                } else {
                    log.warn("⚠️ Не удалось обновить якорное сообщение для чата {}", chatId, e);
                    deactivateAnchorAndRemoveKeyboard(chatId, messageId, false);
                    shouldSendNewMessage = true;
                }
            }
        }

        if (!shouldSendNewMessage) {
            return;
        }

        SendMessage message = new SendMessage(chatId.toString(), text);
        message.setReplyMarkup(markup);
        message.setDisableNotification(true);
        message.setParseMode(TELEGRAM_PARSE_MODE);
        try {
            Message sent = telegramClient.execute(message);
            Integer newAnchorId = sent != null ? sent.getMessageId() : null;
            chatSessionRepository.updateAnchorAndScreen(chatId, newAnchorId, screen, navigationPath);
        } catch (TelegramApiException e) {
            log.error("❌ Ошибка отправки якорного сообщения", e);
        }
    }

    /**
     * Сохраняет существующий якорь при ответе Telegram «message is not modified».
     * <p>
     * Метод обновляет данные в репозитории и при необходимости возвращает постоянную
     * клавиатуру, если она была скрыта пользователем или сценарием.
     * </p>
     *
     * @param chatId    идентификатор чата Telegram
     * @param messageId идентификатор уже отправленного сообщения
     * @param screen    экран, соответствующий якорю
     */
    private void retainAnchorAfterNotModified(Long chatId,
                                              Integer messageId,
                                              BuyerBotScreen screen,
                                              List<BuyerBotScreen> navigationPath) {
        if (chatId == null || messageId == null) {
            return;
        }

        boolean keyboardHiddenBeforeUpdate = chatSessionRepository.isKeyboardHidden(chatId);
        chatSessionRepository.updateAnchorAndScreen(chatId, messageId, screen, navigationPath);
        if (keyboardHiddenBeforeUpdate) {
            ensurePersistentKeyboard(chatId);
        } else {
            chatSessionRepository.markKeyboardVisible(chatId);
        }
    }

    /**
     * Деактивирует старое якорное сообщение и снимает с него инлайн-клавиатуру перед повторной отправкой.
     *
     * @param chatId                 идентификатор чата Telegram
     * @param messageId              идентификатор устаревшего сообщения
     * @param preserveKeyboardStatus следует ли сохранять признак видимости постоянной клавиатуры
     */
    private void deactivateAnchorAndRemoveKeyboard(Long chatId,
                                                   Integer messageId,
                                                   boolean preserveKeyboardStatus) {
        if (chatId == null) {
            return;
        }

        if (preserveKeyboardStatus) {
            chatSessionRepository.deactivateAnchor(chatId);
        } else {
            chatSessionRepository.clearAnchor(chatId);
            chatSessionRepository.markKeyboardHidden(chatId);
        }

        if (messageId != null) {
            removeInlineKeyboard(chatId, messageId);
        }
    }

    /**
     * Обновляет содержимое главного меню, чтобы пользователь видел актуальные кнопки без дублирования сообщений.
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
        if (chatId == null) {
            return;
        }
        SendMessage msg = createPlainMessage(chatId, text);
        try {
            telegramClient.execute(msg);
        } catch (TelegramApiException e) {
            log.error("❌ Ошибка отправки сообщения", e);
        }
    }

    /**
     * Создаёт объект {@link SendMessage} с установленным режимом MarkdownV2 и экранированным текстом.
     *
     * @param chatId идентификатор чата Telegram
     * @param text   исходный текст, который требуется отправить как обычное сообщение
     * @return подготовленный объект {@link SendMessage}
     */
    private SendMessage createPlainMessage(Long chatId, String text) {
        String safeText = escapeMarkdown(text);
        SendMessage message = new SendMessage(chatId.toString(), safeText);
        message.setParseMode(TELEGRAM_PARSE_MODE);
        return message;
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
        chatSessionRepository.markContactRequestSent(chatId);
        SendMessage message = createPlainMessage(chatId, text);
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
        String text = String.format("У нас указано ваше ФИО: %s\nЭто верно?", escapeMarkdown(fullName));
        List<BuyerBotScreen> navigationPath = computeNavigationPath(chatId, BuyerBotScreen.NAME_CONFIRMATION);
        InlineKeyboardMarkup markup = buildNameConfirmationKeyboard(navigationPath);
        sendInlineMessage(chatId, text, markup, BuyerBotScreen.NAME_CONFIRMATION, navigationPath);
    }

    /**
     * Экранирует спецсимволы Markdown, чтобы Telegram корректно обрабатывал пользовательские данные в сообщениях.
     *
     * @param value исходное значение, включаемое в текст с форматированием
     * @return строка с добавленными символами экранирования
     */
    private String escapeMarkdown(String value) {
        if (value == null) {
            return "";
        }
        if (value.isEmpty()) {
            return value;
        }
        StringBuilder escaped = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (isMarkdownV2SpecialCharacter(ch)) {
                escaped.append('\\');
            }
            escaped.append(ch);
        }
        return escaped.toString();
    }

    /**
     * Проверяет, относится ли символ к специальным для MarkdownV2 и требует ли он экранирования.
     *
     * @param ch проверяемый символ
     * @return {@code true}, если символ следует экранировать
     */
    private boolean isMarkdownV2SpecialCharacter(char ch) {
        return switch (ch) {
            case '_', '*', '[', ']', '(', ')', '~', '`', '>', '#', '+', '-', '=', '|', '{', '}', '.', '!', '\\' -> true;
            default -> false;
        };
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

            markActiveAnnouncementSeen(chatId);

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

        SendMessage removalMessage = createPlainMessage(chatId, safeText);
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
