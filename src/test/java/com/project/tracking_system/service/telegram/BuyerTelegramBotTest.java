package com.project.tracking_system.service.telegram;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.project.tracking_system.entity.AdminNotification;
import com.project.tracking_system.entity.BuyerBotScreen;
import com.project.tracking_system.entity.BuyerChatState;
import com.project.tracking_system.entity.Customer;
import com.project.tracking_system.entity.NameSource;
import com.project.tracking_system.service.admin.AdminNotificationService;
import com.project.tracking_system.service.customer.CustomerTelegramService;
import com.project.tracking_system.utils.PhoneUtils;
import com.project.tracking_system.service.telegram.support.InMemoryChatSessionRepository;
import com.project.tracking_system.service.telegram.ChatSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.Chat;
import org.telegram.telegrambots.meta.api.objects.Contact;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboard;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;
import org.telegram.telegrambots.meta.api.objects.chatmember.ChatMemberUpdated;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Тесты для {@link BuyerTelegramBot}, проверяющие распознавание телефона из текста.
 */
@ExtendWith(MockitoExtension.class)
class BuyerTelegramBotTest {

    private static final String MENU_BUTTON_TEXT = "🏠 Меню";

    @Mock
    private TelegramClient telegramClient;

    @Mock
    private CustomerTelegramService telegramService;

    @Mock
    private AdminNotificationService adminNotificationService;

    private BuyerTelegramBot bot;
    private FullNameValidator fullNameValidator;
    private InMemoryChatSessionRepository chatSessionRepository;
    private ObjectMapper objectMapper;
    private AtomicInteger messageIdSequence;

    /**
     * Подготавливает экземпляр бота и стаб под клиента Telegram перед каждым тестом.
     */
    @BeforeEach
    void setUp() {
        fullNameValidator = new FullNameValidator();
        chatSessionRepository = new InMemoryChatSessionRepository();
        objectMapper = new ObjectMapper();
        messageIdSequence = new AtomicInteger(100);
        bot = new BuyerTelegramBot(telegramClient, "token", telegramService, adminNotificationService,
                fullNameValidator, chatSessionRepository, objectMapper);
        doAnswer(invocation -> {
            Message response = new Message();
            response.setMessageId(messageIdSequence.getAndIncrement());
            return response;
        }).when(telegramClient).execute(any(SendMessage.class));
        when(telegramClient.execute(any(EditMessageText.class))).thenReturn(null);
        when(adminNotificationService.findActiveNotification()).thenReturn(Optional.empty());
        when(telegramService.findByChatId(anyLong())).thenReturn(Optional.empty());
    }

    /**
     * Проверяет, что различные форматы номера корректно распознаются и маскируются.
     *
     * @param input        исходная строка, отправленная пользователем
     * @param expectedMask ожидаемое маскированное представление
     */
    @ParameterizedTest
    @MethodSource("recognizedPhones")
    void shouldRecognizePhoneFormatsWhenAwaiting(String input, String expectedMask) throws Exception {
        Long chatId = 123L;
        markAwaitingContact(chatId);

        Update update = mockTextUpdate(chatId, input);

        bot.consume(update);

        ArgumentCaptor<SendMessage> captor = ArgumentCaptor.forClass(SendMessage.class);
        verify(telegramClient).execute(captor.capture());
        SendMessage message = captor.getValue();

        assertEquals(chatId.toString(), message.getChatId());
        assertTrue(message.getText().contains(expectedMask));
        assertPhoneKeyboard(message.getReplyMarkup());
        verify(telegramService).findByChatId(chatId);
        verify(telegramService).updateLastActive(chatId);
        verifyNoMoreInteractions(telegramService);
    }

    /**
     * Убеждается, что при нераспознанной строке выводятся подсказки с форматами номера.
     */
    @Test
    void shouldShowFormatHintForUnrecognizedPhone() throws Exception {
        Long chatId = 456L;
        markAwaitingContact(chatId);

        Update update = mockTextUpdate(chatId, "random text");

        bot.consume(update);

        ArgumentCaptor<SendMessage> captor = ArgumentCaptor.forClass(SendMessage.class);
        verify(telegramClient).execute(captor.capture());
        SendMessage message = captor.getValue();

        assertEquals(chatId.toString(), message.getChatId());
        assertTrue(message.getText().contains("+375"));
        assertTrue(message.getText().contains("8029"));
        assertPhoneKeyboard(message.getReplyMarkup());
        verify(telegramService).findByChatId(chatId);
        verify(telegramService).updateLastActive(chatId);
        verifyNoMoreInteractions(telegramService);
    }

    /**
     * Проверяет, что после команды /start бот отправляет клавиатуру запроса контакта.
     */
    @Test
    void shouldShowPhoneKeyboardOnStartWhenCustomerMissing() throws Exception {
        Long chatId = 321L;
        when(telegramService.findByChatId(chatId)).thenReturn(Optional.empty());

        Update update = mockTextUpdate(chatId, "/start");

        bot.consume(update);

        ArgumentCaptor<SendMessage> captor = ArgumentCaptor.forClass(SendMessage.class);
        verify(telegramClient).execute(captor.capture());
        SendMessage message = captor.getValue();

        assertEquals(chatId.toString(), message.getChatId());
        assertPhoneKeyboard(message.getReplyMarkup());
        assertTrue(message.getText().contains("поделитесь"),
                "Пользователь должен получить просьбу поделиться номером");
    }

    /**
     * Проверяет, что при активном объявлении баннер отображается поверх главного меню.
     */
    @Test
    void shouldRenderActiveAnnouncementInMenu() throws Exception {
        Long chatId = 777L;
        Customer customer = new Customer();
        customer.setTelegramChatId(chatId);
        customer.setNotificationsEnabled(true);
        customer.setFullName("Иван Иванов");
        customer.setNameSource(NameSource.USER_CONFIRMED);
        customer.setLastActiveAt(ZonedDateTime.now().minusHours(2));

        when(telegramService.findByChatId(chatId)).thenReturn(Optional.of(customer));

        AdminNotification notification = new AdminNotification();
        notification.setId(42L);
        notification.setTitle("Новое объявление");
        notification.setBodyLines(List.of("Первый пункт", "Второй пункт"));
        notification.setUpdatedAt(ZonedDateTime.now().minusMinutes(5));
        when(adminNotificationService.findActiveNotification()).thenReturn(Optional.of(notification));

        bot.consume(mockTextUpdate(chatId, "/start"));

        ArgumentCaptor<EditMessageText> editCaptor = ArgumentCaptor.forClass(EditMessageText.class);
        verify(telegramClient, atLeastOnce()).execute(editCaptor.capture());
        EditMessageText bannerEdit = editCaptor.getAllValues().get(editCaptor.getAllValues().size() - 1);

        assertTrue(bannerEdit.getText().contains(notification.getTitle()),
                "Текст баннера должен содержать заголовок объявления");
        assertTrue(bannerEdit.getText().contains("Первый пункт"));
        assertTrue(bannerEdit.getText().contains("Второй пункт"));

        assertNotNull(bannerEdit.getReplyMarkup(), "Ожидалась клавиатура баннера объявления");
        assertTrue(bannerEdit.getReplyMarkup() instanceof InlineKeyboardMarkup,
                "Клавиатура должна быть типа InlineKeyboardMarkup");
        InlineKeyboardMarkup markup = (InlineKeyboardMarkup) bannerEdit.getReplyMarkup();
        InlineKeyboardButton okButton = markup.getKeyboard().get(0).get(0);
        assertEquals("Ок", okButton.getText(), "Кнопка баннера должна называться «Ок»");
        assertEquals("announcement:ack", okButton.getCallbackData());

        ChatSession session = chatSessionRepository.find(chatId)
                .orElseThrow(() -> new AssertionError("Сессия должна быть сохранена"));
        assertEquals(notification.getId(), session.getCurrentNotificationId(),
                "В сессии должен сохраняться идентификатор объявления");
        assertFalse(session.isAnnouncementSeen(),
                "Перед подтверждением объявление не должно считаться просмотренным");
    }

    /**
     * Убеждается, что новым пользователям не показывается баннер объявления до привязки.
     */
    @Test
    void shouldNotShowAnnouncementForNewUser() throws Exception {
        Long chatId = 888L;

        AdminNotification notification = new AdminNotification();
        notification.setId(55L);
        notification.setTitle("Объявление");
        notification.setBodyLines(List.of("Важно"));
        notification.setUpdatedAt(ZonedDateTime.now());
        when(adminNotificationService.findActiveNotification()).thenReturn(Optional.of(notification));
        when(telegramService.findByChatId(chatId)).thenReturn(Optional.empty());

        bot.consume(mockTextUpdate(chatId, "/start"));

        verify(telegramClient, never()).execute(any(EditMessageText.class));

        verify(telegramService).findByChatId(chatId);
        verify(telegramService).updateLastActive(chatId);
        verifyNoMoreInteractions(telegramService);
    }

    /**
     * Убеждается, что при любой слеш-команде в ожидании контакта бот повторно показывает клавиатуру.
     */
    @Test
    void shouldRepeatContactRequestForSlashCommandWhileAwaitingContact() throws Exception {
        Long chatId = 322L;
        markAwaitingContact(chatId);

        Update update = mockTextUpdate(chatId, "/stats");

        bot.consume(update);

        ArgumentCaptor<SendMessage> captor = ArgumentCaptor.forClass(SendMessage.class);
        verify(telegramClient).execute(captor.capture());
        SendMessage message = captor.getValue();

        assertEquals(chatId.toString(), message.getChatId());
        assertPhoneKeyboard(message.getReplyMarkup());
        assertTrue(message.getText().contains("кнопк"),
                "Бот должен напомнить о необходимости воспользоваться кнопкой контакта");
    }

    /**
     * Проверяет, что пустая строка в ожидании контакта тоже приводит к повторному показу клавиатуры.
     */
    @Test
    void shouldRepeatContactRequestForEmptyTextWhileAwaitingContact() throws Exception {
        Long chatId = 323L;
        markAwaitingContact(chatId);

        Update update = mockTextUpdate(chatId, "   ");

        bot.consume(update);

        ArgumentCaptor<SendMessage> captor = ArgumentCaptor.forClass(SendMessage.class);
        verify(telegramClient).execute(captor.capture());
        SendMessage message = captor.getValue();

        assertEquals(chatId.toString(), message.getChatId());
        assertPhoneKeyboard(message.getReplyMarkup());
        assertTrue(message.getText().contains("кнопк"),
                "Бот должен повторно показать кнопку запроса контакта");
    }

    /**
     * Гарантирует, что бот отклоняет контакт, который принадлежит другому пользователю.
     */
    @Test
    void shouldRejectContactFromAnotherUser() throws Exception {
        Long chatId = 987L;
        markAwaitingContact(chatId);

        Update update = mockContactUpdate(chatId, 1_000_000_001L, 2_000_000_002L, "+375291234567");

        bot.consume(update);

        verify(telegramService, never()).linkTelegramToCustomer(anyString(), eq(chatId));

        ArgumentCaptor<SendMessage> captor = ArgumentCaptor.forClass(SendMessage.class);
        verify(telegramClient).execute(captor.capture());
        SendMessage response = captor.getValue();

        assertEquals(chatId.toString(), response.getChatId());
        assertTrue(response.getText().contains("Не удалось подтвердить, что номер принадлежит вам"));
        assertPhoneKeyboard(response.getReplyMarkup());
        assertEquals(BuyerChatState.AWAITING_CONTACT, bot.getState(chatId),
                "После отказа бот должен продолжить ожидать контакт");
    }

    /**
     * Убеждается, что служебное сообщение Telegram со скрытием клавиатуры фиксирует состояние сессии.
     */
    @Test
    void shouldMarkKeyboardHiddenWhenReplyMarkupRequestsRemoval() throws Exception {
        Long chatId = 555L;

        Update update = serviceReplyUpdate(chatId, createRemoveKeyboardMarkup());

        bot.consume(update);

        assertTrue(chatSessionRepository.isKeyboardHidden(chatId),
                "После сообщения remove_keyboard клавиатура должна считаться скрытой");
    }

    /**
     * Проверяет, что наличие обычной inline-клавиатуры не изменяет состояние скрытой клавиатуры.
     */
    @Test
    void shouldIgnoreInlineKeyboardWhileDetectingPersistentRemoval() throws Exception {
        Long chatId = 556L;

        Update update = serviceReplyUpdate(chatId, createInlineKeyboardMarkup());

        bot.consume(update);

        assertFalse(chatSessionRepository.isKeyboardHidden(chatId),
                "Сообщение с inline-клавиатурой не должно скрывать постоянную клавиатуру");
    }

    /**
     * Проверяет, что после события my_chat_member бот восстанавливает главное меню с постоянной клавиатурой.
     */
    @Test
    void shouldRestorePersistentKeyboardWhenMyChatMemberArrives() throws Exception {
        Long chatId = 558L;
        Customer customer = new Customer();
        customer.setTelegramChatId(chatId);
        when(telegramService.findByChatId(chatId)).thenReturn(Optional.of(customer));

        Update update = mockMyChatMemberUpdate(chatId);

        bot.consume(update);

        assertEquals(BuyerChatState.IDLE, bot.getState(chatId),
                "Привязанный покупатель должен вернуться в состояние ожидания команд");

        ArgumentCaptor<SendMessage> captor = ArgumentCaptor.forClass(SendMessage.class);
        verify(telegramClient, atLeastOnce()).execute(captor.capture());

        boolean hasPersistentKeyboard = captor.getAllValues().stream()
                .map(SendMessage::getReplyMarkup)
                .filter(ReplyKeyboardMarkup.class::isInstance)
                .map(ReplyKeyboardMarkup.class::cast)
                .anyMatch(this::keyboardContainsMenuButton);

        assertTrue(hasPersistentKeyboard,
                "После возврата бота в чат клавиатура с кнопкой «🏠 Меню» должна быть переотправлена");
    }

    /**
     * Проверяет, что повторное нажатие кнопки «🏠 Меню» в состоянии ожидания команд
     * приводит лишь к обновлению якорного сообщения без дополнительного уведомления о клавиатуре.
     */
    @Test
    void shouldNotSendKeyboardHintWhenMenuPressedTwiceInIdleState() throws Exception {
        Long chatId = 559L;
        Customer customer = new Customer();
        customer.setTelegramChatId(chatId);
        customer.setNotificationsEnabled(true);
        when(telegramService.findByChatId(chatId)).thenReturn(Optional.of(customer));

        ChatSession session = new ChatSession(chatId,
                BuyerChatState.IDLE,
                101,
                BuyerBotScreen.MENU,
                false,
                false);
        chatSessionRepository.save(session);

        Update update = mockTextUpdate(chatId, MENU_BUTTON_TEXT);

        bot.consume(update);

        ArgumentCaptor<SendMessage> captor = ArgumentCaptor.forClass(SendMessage.class);
        verify(telegramClient, times(1)).execute(captor.capture());

        SendMessage message = captor.getValue();
        assertEquals(chatId.toString(), message.getChatId(),
                "Ответ должен быть отправлен в исходный чат");
        assertNotNull(message.getText(), "Главное меню должно быть переотправлено");
        assertTrue(message.getText().contains("📋 Главное меню"),
                "Переотправленное сообщение должно содержать текст главного меню");
    }

    /**
     * Убеждается, что отсутствие идентификаторов Telegram не позволяет привязать номер.
     *
     * @param senderId      идентификатор отправителя сообщения или {@code null}
     * @param contactUserId идентификатор владельца контакта или {@code null}
     * @param reason        пояснение сценария для сообщений об ошибке
     */
    @ParameterizedTest
    @MethodSource("missingOwnershipIdentifiers")
    void shouldRejectContactWhenOwnershipIdentifiersMissing(Long senderId,
                                                            Long contactUserId,
                                                            String reason) throws Exception {
        Long chatId = 654L;
        markAwaitingContact(chatId);

        Update update = mockContactUpdate(chatId, senderId, contactUserId, "+375291234567");

        bot.consume(update);

        verify(telegramService, never()).linkTelegramToCustomer(anyString(), eq(chatId));

        ArgumentCaptor<SendMessage> captor = ArgumentCaptor.forClass(SendMessage.class);
        verify(telegramClient).execute(captor.capture());
        SendMessage response = captor.getValue();

        assertTrue(response.getText().contains("Не удалось подтвердить, что номер принадлежит вам"),
                () -> "При сценарии '" + reason + "' бот обязан повторно запросить подтверждение");
        assertPhoneKeyboard(response.getReplyMarkup());
        assertEquals(BuyerChatState.AWAITING_CONTACT, bot.getState(chatId),
                "После отказа бот должен продолжить ожидать контакт");
    }

    /**
     * Проверяет, что команда подтверждения имени отправляет сообщение об успешной операции.
     */
    @Test
    void shouldSendSuccessMessageWhenConfirmingNameFromMenu() throws Exception {
        Long chatId = 789L;
        when(telegramService.confirmName(chatId)).thenReturn(true);

        Update update = mockTextUpdate(chatId, "✅ Подтвердить имя");

        bot.consume(update);

        verify(telegramService).confirmName(chatId);

        ArgumentCaptor<SendMessage> captor = ArgumentCaptor.forClass(SendMessage.class);
        verify(telegramClient, atLeastOnce()).execute(captor.capture());

        boolean hasSuccess = captor.getAllValues().stream()
                .map(SendMessage::getText)
                .filter(Objects::nonNull)
                .anyMatch(text -> text.contains("данные подтверждены"));

        assertTrue(hasSuccess, "Бот обязан уведомить пользователя об успешном подтверждении");
    }

    /**
     * Набор корректно распознаваемых телефонных номеров и ожидаемых масок.
     *
     * @return поток аргументов для параметризованного теста
     */
    private static Stream<Arguments> recognizedPhones() {
        return Stream.of(
                "+375291234567",
                "80291234567",
                "8 029 123 45 67"
        ).map(number -> Arguments.of(number,
                PhoneUtils.maskPhone(PhoneUtils.normalizePhone(number))));
    }

    /**
     * Набор сценариев, в которых идентификаторы для подтверждения контакта отсутствуют.
     *
     * @return поток аргументов с комбинациями senderId/contactUserId
     */
    private static Stream<Arguments> missingOwnershipIdentifiers() {
        return Stream.of(
                Arguments.of(null, 2_000_000_002L, "отсутствует идентификатор отправителя"),
                Arguments.of(1_000_000_001L, null, "отсутствует идентификатор владельца контакта"),
                Arguments.of(null, null, "оба идентификатора отсутствуют")
        );
    }

    /**
     * Создаёт мок обновления Telegram с текстовым сообщением пользователя.
     *
     * @param chatId идентификатор чата
     * @param text   текст сообщения
     * @return настроенный объект {@link Update}
     */
    private Update mockTextUpdate(Long chatId, String text) {
        Update update = mock(Update.class);
        Message message = mock(Message.class);

        when(update.hasMessage()).thenReturn(true);
        when(update.getMessage()).thenReturn(message);
        when(message.hasText()).thenReturn(true);
        when(message.getText()).thenReturn(text);
        when(message.getChatId()).thenReturn(chatId);
        when(message.hasContact()).thenReturn(false);

        return update;
    }

    /**
     * Создаёт мок обновления Telegram с контактом для проверки сценариев обработки номеров.
     *
     * @param chatId         идентификатор чата Telegram
     * @param senderUserId   идентификатор отправителя сообщения
     * @param contactUserId  идентификатор владельца контакта
     * @param phoneNumber    номер телефона, указанный в контакте
     * @return сконфигурированный объект {@link Update}
     */
    private Update mockContactUpdate(Long chatId,
                                     Long senderUserId,
                                     Long contactUserId,
                                     String phoneNumber) {
        Update update = mock(Update.class);
        Message message = mock(Message.class);
        Contact contact = mock(Contact.class);
        User fromUser = mock(User.class);

        when(update.hasMessage()).thenReturn(true);
        when(update.getMessage()).thenReturn(message);
        when(message.getChatId()).thenReturn(chatId);
        when(message.hasText()).thenReturn(false);
        when(message.hasContact()).thenReturn(true);
        when(message.getContact()).thenReturn(contact);
        when(message.getFrom()).thenReturn(fromUser);

        when(fromUser.getId()).thenReturn(senderUserId);
        when(contact.getUserId()).thenReturn(contactUserId);
        when(contact.getPhoneNumber()).thenReturn(phoneNumber);

        return update;
    }

    /**
     * Создаёт обновление Telegram типа my_chat_member для проверки восстановления клавиатуры.
     *
     * @param chatId идентификатор чата Telegram
     * @return сконфигурированный объект {@link Update}
     */
    private Update mockMyChatMemberUpdate(Long chatId) {
        Update update = mock(Update.class);
        ChatMemberUpdated myChatMember = mock(ChatMemberUpdated.class);
        Chat chat = mock(Chat.class);

        when(update.hasMyChatMember()).thenReturn(true);
        when(update.getMyChatMember()).thenReturn(myChatMember);
        when(myChatMember.getChat()).thenReturn(chat);
        when(chat.getId()).thenReturn(chatId);

        return update;
    }

    /**
     * Создаёт обновление Telegram со служебным reply_markup.
     *
     * @param chatId      идентификатор чата Telegram
     * @param replyMarkup узел с данными reply_markup
     * @return объект {@link Update} с заполненным сообщением
     */
    private Update serviceReplyUpdate(Long chatId, ObjectNode replyMarkup) throws JsonProcessingException {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("message_id", 1);
        root.put("date", 0);
        ObjectNode chat = root.putObject("chat");
        chat.put("id", chatId);
        chat.put("type", "private");
        root.set("reply_markup", replyMarkup);

        Message message = objectMapper.treeToValue(root, Message.class);

        Update update = new Update();
        update.setMessage(message);
        return update;
    }

    /**
     * Формирует JSON-инструкцию для скрытия постоянной клавиатуры.
     *
     * @return узел reply_markup с флагом remove_keyboard
     */
    private ObjectNode createRemoveKeyboardMarkup() {
        ObjectNode replyMarkup = objectMapper.createObjectNode();
        replyMarkup.put("remove_keyboard", true);
        return replyMarkup;
    }

    /**
     * Формирует JSON inline-клавиатуры для проверки отсутствия скрытия клавиатуры.
     *
     * @return узел reply_markup с обычной inline-клавиатурой
     */
    private ObjectNode createInlineKeyboardMarkup() {
        ObjectNode replyMarkup = objectMapper.createObjectNode();
        ArrayNode keyboard = replyMarkup.putArray("inline_keyboard");
        ArrayNode row = keyboard.addArray();
        ObjectNode button = row.addObject();
        button.put("text", "Demo");
        button.put("callback_data", "demo");
        return replyMarkup;
    }

    /**
     * Помечает чат как ожидающий контакта через отражение приватного состояния бота.
     *
     * @param chatId идентификатор чата Telegram
     */
    private void markAwaitingContact(Long chatId) throws Exception {
        chatSessionRepository.updateState(chatId, BuyerChatState.AWAITING_CONTACT);
    }

    /**
     * Проверяет, что к сообщению прикреплена клавиатура с кнопкой запроса контакта.
     *
     * @param replyKeyboard клавиатура, отправленная ботом
     */
    private void assertPhoneKeyboard(ReplyKeyboard replyKeyboard) {
        assertNotNull(replyKeyboard, "Ожидалась клавиатура с запросом телефона");
        assertTrue(replyKeyboard instanceof ReplyKeyboardMarkup,
                "Клавиатура должна быть типа ReplyKeyboardMarkup");
        ReplyKeyboardMarkup markup = (ReplyKeyboardMarkup) replyKeyboard;
        List<KeyboardRow> rows = markup.getKeyboard();
        assertNotNull(rows);
        assertFalse(rows.isEmpty(), "Клавиатура должна содержать хотя бы одну строку");

        boolean hasRequestContact = false;
        for (KeyboardRow row : rows) {
            for (KeyboardButton button : row) {
                if (Boolean.TRUE.equals(button.getRequestContact())) {
                    hasRequestContact = true;
                    break;
                }
            }
            if (hasRequestContact) {
                break;
            }
        }
        assertTrue(hasRequestContact, "Кнопка с запросом контакта должна присутствовать");
    }

    /**
     * Проверяет наличие кнопки «🏠 Меню» в постоянной клавиатуре.
     *
     * @param markup проверяемая клавиатура
     * @return {@code true}, если кнопка найдена
     */
    private boolean keyboardContainsMenuButton(ReplyKeyboardMarkup markup) {
        if (markup == null || markup.getKeyboard() == null) {
            return false;
        }

        return markup.getKeyboard().stream()
                .filter(Objects::nonNull)
                .flatMap(row -> row.stream().filter(Objects::nonNull))
                .map(KeyboardButton::getText)
                .anyMatch(MENU_BUTTON_TEXT::equals);
    }
}
