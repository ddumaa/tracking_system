package com.project.tracking_system.service.telegram;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.tracking_system.entity.AdminNotification;
import com.project.tracking_system.entity.BuyerBotScreen;
import com.project.tracking_system.entity.BuyerChatState;
import com.project.tracking_system.dto.TelegramParcelInfoDTO;
import com.project.tracking_system.dto.TelegramParcelsOverviewDTO;
import com.project.tracking_system.entity.Customer;
import com.project.tracking_system.entity.GlobalStatus;
import com.project.tracking_system.entity.NameSource;
import com.project.tracking_system.entity.OrderReturnRequest;
import com.project.tracking_system.service.admin.AdminNotificationService;
import com.project.tracking_system.service.customer.CustomerTelegramService;
import com.project.tracking_system.service.telegram.ChatSession;
import com.project.tracking_system.service.telegram.support.InMemoryChatSessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageReplyMarkup;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.chat.Chat;
import org.telegram.telegrambots.meta.api.objects.chatmember.ChatMemberUpdated;
import org.telegram.telegrambots.meta.api.objects.Contact;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardRemove;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import org.telegram.telegrambots.meta.generics.TelegramClient;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Интеграционные сценарии для {@link BuyerTelegramBot}, проверяющие переходы между состояниями.
 */
@ExtendWith(MockitoExtension.class)
class BuyerTelegramBotStateIntegrationTest {

    @Mock
    private TelegramClient telegramClient;

    @Mock
    private CustomerTelegramService telegramService;

    @Mock
    private AdminNotificationService adminNotificationService;

    private BuyerTelegramBot bot;
    private FullNameValidator fullNameValidator;
    private InMemoryChatSessionRepository chatSessionRepository;
    private AtomicInteger messageIdSequence;

    /**
     * Создаёт экземпляр бота перед каждым сценарием и стабилизирует клиент Telegram.
     */
    @BeforeEach
    void setUp() throws Exception {
        fullNameValidator = new FullNameValidator();
        chatSessionRepository = new InMemoryChatSessionRepository();
        messageIdSequence = new AtomicInteger(500);
        bot = new BuyerTelegramBot(telegramClient, "token", telegramService, adminNotificationService,
                fullNameValidator, chatSessionRepository, new ObjectMapper());
        lenient().when(adminNotificationService.findActiveNotification()).thenReturn(Optional.empty());
        lenient().when(telegramService.findByChatId(anyLong())).thenReturn(Optional.empty());
        lenient().when(telegramService.getActiveReturnRequests(anyLong())).thenReturn(List.of());
        lenient().when(telegramService.registerReturnRequestFromTelegram(anyLong(), anyLong(), anyString(), anyString()))
                .thenReturn(new OrderReturnRequest());
        lenient().when(telegramService.approveExchangeFromTelegram(anyLong(), anyLong(), anyLong())).thenReturn(null);
        lenient().when(telegramClient.execute(any(EditMessageText.class))).thenReturn(null);
        lenient().when(telegramClient.execute(any(EditMessageReplyMarkup.class))).thenReturn(null);
        lenient().when(telegramClient.execute(any(AnswerCallbackQuery.class))).thenReturn(null);
        doAnswer(invocation -> {
            Message sent = new Message();
            sent.setMessageId(messageIdSequence.getAndIncrement());
            return sent;
        }).when(telegramClient).execute(any(SendMessage.class));
    }

    /**
     * Проверяет, что после команды /start бот переходит в ожидание контакта.
     */
    @Test
    void shouldTransitionToAwaitingContactAfterStart() throws Exception {
        Long chatId = 1001L;
        when(telegramService.findByChatId(chatId)).thenReturn(Optional.empty());

        bot.consume(textUpdate(chatId, "/start"));

        assertEquals(BuyerChatState.AWAITING_CONTACT, bot.getState(chatId),
                "Состояние должно перейти в ожидание контакта");

        ArgumentCaptor<SendMessage> captor = ArgumentCaptor.forClass(SendMessage.class);
        verify(telegramClient).execute(captor.capture());
        SendMessage message = captor.getValue();

        assertTrue(message.getText().contains("номер"),
                "Пользователь должен получить приглашение поделиться номером");
        assertPhoneKeyboard(message);
    }

    /**
     * Проверяет, что при сценарии «my_chat_member → /start» запрос контакта отправляется один раз.
     */
    @Test
    void shouldNotDuplicateContactRequestAfterMyChatMemberAndStart() throws Exception {
        Long chatId = 1111L;
        when(telegramService.findByChatId(chatId)).thenReturn(Optional.empty());

        bot.consume(myChatMemberUpdate(chatId));
        bot.consume(textUpdate(chatId, "/start"));

        ArgumentCaptor<SendMessage> captor = ArgumentCaptor.forClass(SendMessage.class);
        verify(telegramClient, times(1)).execute(captor.capture());

        SendMessage message = captor.getValue();
        assertPhoneKeyboard(message);
        assertEquals(BuyerChatState.AWAITING_CONTACT, bot.getState(chatId),
                "После повторного /start бот должен оставаться в ожидании контакта без лишних сообщений");
        assertTrue(chatSessionRepository.isContactRequestSent(chatId),
                "Флаг отправленного запроса контакта обязан сохраняться в сессии");
    }

    /**
     * Проверяет, что подтверждение объявления помечает его просмотренным и предотвращает повторные клики.
     */
    @Test
    void shouldHandleRepeatedAnnouncementAck() throws Exception {
        Long chatId = 1313L;

        Customer customer = new Customer();
        customer.setTelegramChatId(chatId);
        customer.setNotificationsEnabled(true);
        customer.setFullName("Иван Иванов");
        customer.setNameSource(NameSource.USER_CONFIRMED);
        customer.setTelegramConfirmed(true);

        when(telegramService.findByChatId(chatId)).thenReturn(Optional.of(customer));

        AdminNotification notification = new AdminNotification();
        notification.setId(77L);
        notification.setTitle("Новое объявление");
        notification.setBodyLines(List.of("Тестовое сообщение"));
        notification.setUpdatedAt(ZonedDateTime.now().minusMinutes(20));
        when(adminNotificationService.findActiveNotification()).thenReturn(Optional.of(notification));

        bot.consume(textUpdate(chatId, "/start"));

        ChatSession session = chatSessionRepository.find(chatId)
                .orElseThrow(() -> new AssertionError("После /start должна появиться сессия"));
        Integer anchorMessageId = session.getAnchorMessageId();
        assertNotNull(anchorMessageId, "После показа баннера должен фиксироваться якорь");

        clearInvocations(telegramClient);

        bot.consume(callbackUpdate(chatId, anchorMessageId, "announcement:ack"));

        ArgumentCaptor<AnswerCallbackQuery> answerCaptor = ArgumentCaptor.forClass(AnswerCallbackQuery.class);
        verify(telegramClient).execute(answerCaptor.capture());
        assertEquals("Готово", answerCaptor.getValue().getText(),
                "Первое подтверждение должно завершаться сообщением о готовности");

        ArgumentCaptor<EditMessageText> editCaptor = ArgumentCaptor.forClass(EditMessageText.class);
        verify(telegramClient).execute(editCaptor.capture());
        assertTrue(editCaptor.getValue().getText().contains("Главное меню"),
                "После подтверждения бот обязан вернуть экран меню");

        assertTrue(chatSessionRepository.isAnnouncementSeen(chatId),
                "После подтверждения объявление должно помечаться просмотренным");

        clearInvocations(telegramClient);

        bot.consume(callbackUpdate(chatId, anchorMessageId, "announcement:ack"));

        verify(telegramClient).execute(answerCaptor.capture());
        AnswerCallbackQuery spamAnswer = answerCaptor.getAllValues()
                .get(answerCaptor.getAllValues().size() - 1);
        assertEquals("Уведомление уже закрыто", spamAnswer.getText(),
                "Повторный клик должен завершаться антиспам-сообщением");
        verify(telegramClient, never()).execute(any(EditMessageText.class));
    }

    /**
     * Проверяет, что после цепочки «my_chat_member → /start» остаётся одно сообщение главного меню с актуальной клавиатурой.
     */
    @Test
    void shouldKeepSingleMenuMessageAfterMyChatMemberAndStart() throws Exception {
        Long chatId = 2222L;
        int anchorId = 321;

        Customer customer = new Customer();
        customer.setTelegramConfirmed(true);
        customer.setNameSource(NameSource.USER_CONFIRMED);
        customer.setNotificationsEnabled(true);
        customer.setFullName("Иван Иванов");

        chatSessionRepository.updateAnchorAndScreen(chatId,
                anchorId,
                BuyerBotScreen.MENU,
                List.of(BuyerBotScreen.MENU));
        chatSessionRepository.updateState(chatId, BuyerChatState.IDLE);
        chatSessionRepository.markKeyboardVisible(chatId);

        when(telegramService.findByChatId(chatId)).thenReturn(Optional.of(customer));

        AtomicInteger editCalls = new AtomicInteger();
        when(telegramClient.execute(any(EditMessageText.class))).thenAnswer(invocation -> {
            if (editCalls.incrementAndGet() == 2) {
                throw new TelegramApiException("Bad Request: message is not modified");
            }
            return null;
        });

        bot.consume(myChatMemberUpdate(chatId));
        bot.consume(textUpdate(chatId, "/start"));

        ArgumentCaptor<EditMessageText> editCaptor = ArgumentCaptor.forClass(EditMessageText.class);
        verify(telegramClient, times(2)).execute(editCaptor.capture());
        List<EditMessageText> editRequests = editCaptor.getAllValues();

        for (EditMessageText edit : editRequests) {
            assertEquals(anchorId, edit.getMessageId(),
                    "Бот обязан переиспользовать исходное сообщение главного меню");
            assertNotNull(edit.getReplyMarkup(),
                    "Главное меню должно оставаться с инлайн-клавиатурой");
            assertTrue(edit.getText().contains("Главное меню"),
                    "Текст сообщения должен содержать заголовок главного меню");
            InlineKeyboardMarkup markup = (InlineKeyboardMarkup) edit.getReplyMarkup();
            assertTrue(containsMenuInlineButtons(markup),
                    "Клавиатура главного меню должна содержать стандартные кнопки");
        }

        ArgumentCaptor<SendMessage> messageCaptor = ArgumentCaptor.forClass(SendMessage.class);
        verify(telegramClient, atLeastOnce()).execute(messageCaptor.capture());
        long menuMessages = messageCaptor.getAllValues().stream()
                .filter(this::isMainMenuAnchorMessage)
                .count();
        assertEquals(0L, menuMessages,
                "Бот не должен создавать дополнительное сообщение «Главное меню» после команды /start");

        ChatSession session = chatSessionRepository.find(chatId)
                .orElseThrow(() -> new AssertionError("После обновления должна существовать сессия чата"));

        assertEquals(anchorId, session.getAnchorMessageId(),
                "В сессии необходимо сохранить идентификатор исходного сообщения главного меню");
        assertEquals(BuyerBotScreen.MENU, session.getLastScreen(),
                "Последний экран обязан соответствовать главному меню");
        assertFalse(chatSessionRepository.isKeyboardHidden(chatId),
                "После восстановления меню постоянная клавиатура должна считаться показанной");
    }

    /**
     * Проверяет, что после привязки контакта клавиатура меню возвращается, а кнопка запроса номера исчезает.
     */
    @Test
    void shouldShowMenuKeyboardAfterContactForNewUser() throws Exception {
        Long chatId = 3030L;
        Customer customer = new Customer();
        customer.setTelegramConfirmed(false);
        customer.setNameSource(NameSource.MERCHANT_PROVIDED);
        customer.setNotificationsEnabled(true);
        customer.setFullName(null);

        when(telegramService.findByChatId(chatId))
                .thenReturn(Optional.empty(), Optional.of(customer));
        when(telegramService.linkTelegramToCustomer(anyString(), eq(chatId))).thenReturn(customer);
        when(telegramService.confirmTelegram(customer)).thenReturn(customer);
        doNothing().when(telegramService).notifyActualStatuses(customer);

        bot.consume(textUpdate(chatId, "/start"));
        clearInvocations(telegramClient);

        bot.consume(contactUpdate(chatId, "+375291234567"));

        assertEquals(BuyerChatState.AWAITING_NAME_INPUT, bot.getState(chatId),
                "После привязки номера бот должен ждать ввод ФИО и показывать меню-клавиатуру");

        assertFalse(chatSessionRepository.isKeyboardHidden(chatId),
                "После возврата в меню клавиатура с кнопкой «🏠 Меню» должна считаться видимой");

        ArgumentCaptor<SendMessage> captor = ArgumentCaptor.forClass(SendMessage.class);
        verify(telegramClient, atLeastOnce()).execute(captor.capture());
        List<SendMessage> messages = captor.getAllValues();

        int removalMessageIndex = -1;
        int menuKeyboardIndex = -1;

        for (int i = 0; i < messages.size(); i++) {
            var markup = messages.get(i).getReplyMarkup();
            if (markup instanceof ReplyKeyboardRemove) {
                removalMessageIndex = i;
            }
            if (markup instanceof ReplyKeyboardMarkup replyKeyboardMarkup
                    && containsOnlyMenuButton(replyKeyboardMarkup)) {
                if (menuKeyboardIndex < 0) {
                    menuKeyboardIndex = i;
                }
            }
        }

        assertTrue(removalMessageIndex >= 0,
                "После получения контакта бот должен скрыть временную клавиатуру запросом ReplyKeyboardRemove");
        assertTrue(menuKeyboardIndex >= 0,
                "После удаления временной клавиатуры бот должен вернуть постоянную клавиатуру меню");
        assertTrue(menuKeyboardIndex > removalMessageIndex,
                "Клавиатура меню должна появляться после сообщения с ReplyKeyboardRemove");

        boolean hasContactButton = messages.stream()
                .map(SendMessage::getReplyMarkup)
                .filter(ReplyKeyboardMarkup.class::isInstance)
                .map(ReplyKeyboardMarkup.class::cast)
                .anyMatch(this::containsContactButton);
        assertFalse(hasContactButton,
                "Кнопка «📱 Поделиться номером» не должна присутствовать после возврата в меню");
    }

    /**
     * Проверяет, что после привязки контакта пользователь пропускает текущее объявление, но видит следующее активное.
     */
    @Test
    void shouldSkipExistingAnnouncementAfterContactAndShowNextActivation() throws Exception {
        Long chatId = 3131L;

        Customer customer = new Customer();
        customer.setNotificationsEnabled(true);
        customer.setFullName("Иван Иванов");
        customer.setNameSource(NameSource.USER_CONFIRMED);
        customer.setTelegramConfirmed(false);

        when(telegramService.linkTelegramToCustomer(anyString(), eq(chatId))).thenAnswer(invocation -> {
            customer.setTelegramChatId(chatId);
            return customer;
        });
        when(telegramService.confirmTelegram(customer)).thenAnswer(invocation -> {
            customer.setTelegramConfirmed(true);
            return customer;
        });
        doNothing().when(telegramService).notifyActualStatuses(customer);
        when(telegramService.findByChatId(chatId)).thenAnswer(invocation ->
                Optional.ofNullable(customer.getTelegramChatId() != null ? customer : null));

        AdminNotification initialNotification = new AdminNotification();
        initialNotification.setId(88L);
        initialNotification.setTitle("Старое объявление");
        initialNotification.setBodyLines(List.of("Первая версия"));
        ZonedDateTime initialUpdatedAt = ZonedDateTime.now().minusMinutes(40);
        initialNotification.setUpdatedAt(initialUpdatedAt);

        AdminNotification nextNotification = new AdminNotification();
        nextNotification.setId(89L);
        nextNotification.setTitle("Обновлённое объявление");
        nextNotification.setBodyLines(List.of("Новый пункт"));
        ZonedDateTime nextUpdatedAt = initialUpdatedAt.plusMinutes(5);
        nextNotification.setUpdatedAt(nextUpdatedAt);

        AtomicReference<AdminNotification> activeNotification = new AtomicReference<>(initialNotification);
        when(adminNotificationService.findActiveNotification()).thenAnswer(invocation ->
                Optional.ofNullable(activeNotification.get()));

        bot.consume(textUpdate(chatId, "/start"));
        clearInvocations(telegramClient);

        bot.consume(contactUpdate(chatId, "+375297000000"));

        ChatSession afterContact = chatSessionRepository.find(chatId)
                .orElseThrow(() -> new AssertionError("После привязки должна существовать сессия"));
        assertEquals(initialNotification.getId(), afterContact.getCurrentNotificationId(),
                "Активное объявление должно быть зафиксировано после привязки");
        assertTrue(afterContact.isAnnouncementSeen(),
                "Новый пользователь должен считаться ознакомленным с текущим объявлением");
        assertEquals(initialUpdatedAt, afterContact.getAnnouncementUpdatedAt(),
                "В сессии должно сохраняться время актуального объявления");
        assertNull(afterContact.getAnnouncementAnchorMessageId(),
                "Баннер не должен отправляться до смены объявления");

        clearInvocations(telegramClient);

        activeNotification.set(nextNotification);

        bot.consume(textUpdate(chatId, "/start"));

        boolean nextAnnouncementShown = mockingDetails(telegramClient).getInvocations().stream()
                .filter(invocation -> "execute".equals(invocation.getMethod().getName()))
                .map(invocation -> invocation.getArgument(0))
                .flatMap(request -> {
                    if (request instanceof SendMessage sendMessage) {
                        return Stream.ofNullable(sendMessage.getText());
                    }
                    if (request instanceof EditMessageText editMessageText) {
                        return Stream.ofNullable(editMessageText.getText());
                    }
                    return Stream.empty();
                })
                .filter(Objects::nonNull)
                .anyMatch(text -> text.contains(nextNotification.getTitle()));

        assertTrue(nextAnnouncementShown,
                "После активации нового объявления пользователь обязан увидеть баннер");

        ChatSession afterActivation = chatSessionRepository.find(chatId)
                .orElseThrow(() -> new AssertionError("Сессия должна обновиться после активации объявления"));
        assertEquals(nextNotification.getId(), afterActivation.getCurrentNotificationId(),
                "Состояние должно ссылаться на новое объявление");
        assertFalse(afterActivation.isAnnouncementSeen(),
                "Новое объявление не считается просмотренным до подтверждения");
        assertEquals(nextUpdatedAt, afterActivation.getAnnouncementUpdatedAt(),
                "В сессии должно сохраняться время обновления нового объявления");
        assertNotNull(afterActivation.getAnnouncementAnchorMessageId(),
                "После показа баннера должен сохраняться идентификатор сообщения");
    }

    /**
     * Проверяет, что при ответе Telegram «message is not modified» бот повторно использует существующее меню.
     */
    @Test
    void shouldKeepMenuAnchorWhenMessageNotModifiedAfterContact() throws Exception {
        Long chatId = 9090L;
        Integer previousAnchorId = 555;

        ChatSession existingSession = new ChatSession(chatId,
                BuyerChatState.AWAITING_CONTACT,
                previousAnchorId,
                BuyerBotScreen.MENU,
                false,
                true);
        chatSessionRepository.save(existingSession);

        Customer customer = new Customer();
        customer.setTelegramConfirmed(false);
        customer.setNameSource(NameSource.MERCHANT_PROVIDED);
        customer.setNotificationsEnabled(true);
        customer.setFullName("Иван Иванов");

        when(telegramService.linkTelegramToCustomer(anyString(), eq(chatId))).thenReturn(customer);
        when(telegramService.confirmTelegram(customer)).thenReturn(customer);
        doNothing().when(telegramService).notifyActualStatuses(customer);
        when(telegramService.findByChatId(chatId)).thenReturn(Optional.of(customer));

        doThrow(new TelegramApiException("Bad Request: message is not modified"))
                .when(telegramClient).execute(any(EditMessageText.class));

        Update update = contactUpdate(chatId, "+375298888888");
        update.getMessage().setFrom(createUser(chatId));

        bot.consume(update);

        ArgumentCaptor<SendMessage> captor = ArgumentCaptor.forClass(SendMessage.class);
        verify(telegramClient, atLeastOnce()).execute(captor.capture());

        boolean hasMenuMessage = captor.getAllValues().stream()
                .anyMatch(this::isMainMenuAnchorMessage);
        assertFalse(hasMenuMessage,
                "При ошибке message is not modified бот не должен создавать новое сообщение главного меню");

        ChatSession updatedSession = chatSessionRepository.find(chatId).orElseThrow();
        assertEquals(previousAnchorId, updatedSession.getAnchorMessageId(),
                "После ошибки message is not modified должен сохраняться прежний якорь меню");
        assertEquals(BuyerBotScreen.MENU, updatedSession.getLastScreen(),
                "Последний экран обязан оставаться главным меню при повторном использовании сообщения");

        verify(telegramClient).execute(any(EditMessageText.class));
        verify(telegramClient, never()).execute(any(EditMessageReplyMarkup.class));
    }

    /**
     * Проверяет, что полный сценарий возврата вызывает регистрацию заявки в доменном сервисе.
     */
    @Test
    void shouldRegisterReturnRequestAfterSuccessfulFlow() throws Exception {
        Long chatId = 4545L;
        Integer callbackMessageId = 900;
        Long parcelId = 7777L;

        TelegramParcelInfoDTO parcelInfo = new TelegramParcelInfoDTO(parcelId, "TR-777", "Магазин", GlobalStatus.DELIVERED, false);
        TelegramParcelsOverviewDTO overview = new TelegramParcelsOverviewDTO(List.of(parcelInfo), List.of(), List.of());
        when(telegramService.getParcelsOverview(chatId)).thenReturn(Optional.of(overview));

        ArgumentCaptor<String> idempotencyCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> reasonCaptor = ArgumentCaptor.forClass(String.class);

        OrderReturnRequest savedRequest = new OrderReturnRequest();
        doReturn(savedRequest).when(telegramService).registerReturnRequestFromTelegram(
                eq(chatId),
                eq(parcelId),
                idempotencyCaptor.capture(),
                reasonCaptor.capture()
        );

        bot.consume(callbackUpdate(chatId, callbackMessageId, "parcel:return:" + parcelId));
        bot.consume(callbackUpdate(chatId, callbackMessageId, "returns:create:reason:not_fit"));

        verify(telegramService).registerReturnRequestFromTelegram(eq(chatId), eq(parcelId), anyString(), anyString());
        assertFalse(idempotencyCaptor.getValue().isBlank(), "Бот обязан передавать непустой идемпотентный ключ");
        assertEquals("Не подошло", reasonCaptor.getValue(), "Причина возврата должна передаваться в сервис без изменений");

        ArgumentCaptor<SendMessage> messageCaptor = ArgumentCaptor.forClass(SendMessage.class);
        verify(telegramClient, atLeastOnce()).execute(messageCaptor.capture());
        SendMessage summary = messageCaptor.getAllValues().stream()
                .filter(message -> message.getText() != null)
                .filter(message -> message.getText().contains("Зафиксировали запрос на возврат"))
                .reduce((first, second) -> second)
                .orElseThrow(() -> new AssertionError("Итоговое сообщение не найдено"));
        String summaryText = summary.getText();
        assertTrue(summaryText.contains("📂 Текущие заявки"),
                "Пользователь должен получить подсказку о разделе текущих заявок");
        assertTrue(summary.getReplyMarkup() instanceof InlineKeyboardMarkup,
                "Финальное сообщение должно сопровождаться инлайн-клавиатурой");
        InlineKeyboardMarkup markup = (InlineKeyboardMarkup) summary.getReplyMarkup();
        boolean hasDoneButton = markup.getKeyboard().stream()
                .filter(Objects::nonNull)
                .flatMap(List::stream)
                .filter(Objects::nonNull)
                .anyMatch(button -> "Хорошо".equals(button.getText())
                        && "returns:done".equals(button.getCallbackData()));
        assertTrue(hasDoneButton, "Подтверждение должно содержать кнопку возврата в меню");
        boolean hasActiveButton = markup.getKeyboard().stream()
                .filter(Objects::nonNull)
                .flatMap(List::stream)
                .filter(Objects::nonNull)
                .anyMatch(button -> "📂 Текущие заявки".equals(button.getText())
                        && "returns:active".equals(button.getCallbackData()));
        assertTrue(hasActiveButton, "Финальная клавиатура должна позволять открыть текущие заявки");
        assertEquals(BuyerChatState.IDLE, bot.getState(chatId), "После завершения сценария бот обязан вернуть состояние IDLE");
    }

    /**
     * Проверяет, что при ожидании причины возврата нажатие кнопки «🏠 Меню»
     * возвращает пользователя в главное меню и очищает контекст оформления.
     */
    @Test
    void shouldReturnToMenuFromReasonAwaitingOnPersistentButton() throws Exception {
        Long chatId = 4546L;
        Integer callbackMessageId = 901;
        Long parcelId = 8888L;

        Customer customer = new Customer();
        customer.setTelegramConfirmed(true);
        customer.setNotificationsEnabled(true);
        customer.setNameSource(NameSource.USER_CONFIRMED);
        customer.setFullName("Антон Смирнов");

        when(telegramService.findByChatId(chatId)).thenReturn(Optional.of(customer));

        TelegramParcelInfoDTO parcelInfo = new TelegramParcelInfoDTO(parcelId, "TR-888", "Магазин А", GlobalStatus.DELIVERED, false);
        TelegramParcelsOverviewDTO overview = new TelegramParcelsOverviewDTO(List.of(parcelInfo), List.of(), List.of());
        when(telegramService.getParcelsOverview(chatId)).thenReturn(Optional.of(overview));

        bot.consume(callbackUpdate(chatId, callbackMessageId, "returns:create"));
        bot.consume(callbackUpdate(chatId, callbackMessageId, "returns:create:type:return"));

        String storeKey = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("Магазин А".getBytes(StandardCharsets.UTF_8));
        bot.consume(callbackUpdate(chatId, callbackMessageId, "returns:create:store:" + storeKey));
        bot.consume(callbackUpdate(chatId, callbackMessageId, "returns:create:parcel:" + parcelId));

        assertEquals(BuyerChatState.AWAITING_RETURN_REASON, bot.getState(chatId),
                "После выбора посылки бот должен ожидать причину возврата");

        ChatSession activeSession = chatSessionRepository.find(chatId)
                .orElseThrow(() -> new AssertionError("Контекст возврата обязан сохраниться в сессии"));
        assertEquals(parcelId, activeSession.getReturnParcelId(),
                "Перед возвратом в меню должен храниться идентификатор посылки");
        assertNotNull(activeSession.getReturnIdempotencyKey(),
                "Сценарий возврата обязан сформировать идемпотентный ключ");

        clearInvocations(telegramClient);

        bot.consume(textUpdate(chatId, "🏠 Меню"));

        assertEquals(BuyerChatState.IDLE, bot.getState(chatId),
                "Нажатие кнопки меню должно переводить сценарий в состояние IDLE");

        ChatSession sessionAfterMenu = chatSessionRepository.find(chatId)
                .orElseThrow(() -> new AssertionError("Данные сессии обязаны сохраняться после возврата в меню"));
        assertNull(sessionAfterMenu.getReturnParcelId(),
                "После возврата в меню идентификатор посылки должен быть очищен");
        assertNull(sessionAfterMenu.getReturnReason(),
                "Контекст причины возврата обязан сбрасываться");
        assertNull(sessionAfterMenu.getReturnIdempotencyKey(),
                "После нажатия меню не должно оставаться идемпотентного ключа");

        ArgumentCaptor<SendMessage> messageCaptor = ArgumentCaptor.forClass(SendMessage.class);
        verify(telegramClient, atLeastOnce()).execute(messageCaptor.capture());
        boolean hasReminder = messageCaptor.getAllValues().stream()
                .map(SendMessage::getText)
                .filter(Objects::nonNull)
                .anyMatch(text -> text.contains("⚠️ Пожалуйста, выберите причину с помощью кнопок ниже."));
        assertFalse(hasReminder,
                "Возврат в меню не должен сопровождаться повторным напоминанием о выборе причины");
    }

    /**
     * Проверяет, что при повторной регистрации выводится сообщение об уже обработке заявки.
     */
    @Test
    void shouldNotifyAboutActiveRequestWhenRegistrationRejected() throws Exception {
        Long chatId = 4646L;
        Integer callbackMessageId = 901;
        Long parcelId = 8888L;

        TelegramParcelInfoDTO parcelInfo = new TelegramParcelInfoDTO(parcelId, "TR-888", "Магазин", GlobalStatus.DELIVERED, false);
        when(telegramService.getParcelsOverview(chatId)).thenReturn(Optional.of(new TelegramParcelsOverviewDTO(
                List.of(parcelInfo), List.of(), List.of())));

        doThrow(new IllegalStateException("У посылки уже есть активная заявка на возврат"))
                .when(telegramService).registerReturnRequestFromTelegram(
                        eq(chatId), eq(parcelId), anyString(), anyString());

        bot.consume(callbackUpdate(chatId, callbackMessageId, "parcel:return:" + parcelId));
        bot.consume(callbackUpdate(chatId, callbackMessageId, "returns:create:reason:not_fit"));

        ArgumentCaptor<SendMessage> messageCaptor = ArgumentCaptor.forClass(SendMessage.class);
        verify(telegramClient, atLeastOnce()).execute(messageCaptor.capture());
        boolean hasAlreadyProcessingMessage = messageCaptor.getAllValues().stream()
                .map(SendMessage::getText)
                .anyMatch(text -> text.contains("уже оформлена активная заявка"));
        assertTrue(hasAlreadyProcessingMessage, "Бот обязан предупредить пользователя о существующей заявке");
        assertEquals(BuyerChatState.IDLE, bot.getState(chatId), "После ошибки состояние должно сбрасываться к IDLE");
    }

    /**
     * Создаёт пользователя Telegram с минимально необходимыми данными для тестов.
     *
     * @param chatId идентификатор пользователя Telegram
     * @return объект {@link User} с заполненным идентификатором
     */
    private User createUser(Long chatId) {
        return new User(chatId, "TestUser", false);
    }

    /**
     * Убеждается, что повторная команда /start не дублирует сообщение о быстрых клавишах,
     * когда клавиатура уже показана пользователю.
     */
    @Test
    void shouldSendQuickAccessHintOnlyOnceWhenKeyboardVisible() throws Exception {
        Long chatId = 6060L;
        Customer customer = new Customer();
        customer.setTelegramConfirmed(true);
        customer.setNameSource(NameSource.USER_CONFIRMED);
        customer.setNotificationsEnabled(true);
        customer.setFullName("Иван Иванов");

        when(telegramService.findByChatId(chatId)).thenReturn(Optional.of(customer));

        AtomicInteger messageIdSequence = new AtomicInteger(1000);

        try {
            when(telegramClient.execute(any(SendMessage.class))).thenAnswer(invocation -> {
                Message sent = new Message();
                sent.setMessageId(messageIdSequence.incrementAndGet());
                return sent;
            });

            bot.consume(textUpdate(chatId, "/start"));
            bot.consume(textUpdate(chatId, "/start"));

            ArgumentCaptor<SendMessage> captor = ArgumentCaptor.forClass(SendMessage.class);
            verify(telegramClient, atLeastOnce()).execute(captor.capture());

            long quickAccessMessages = captor.getAllValues().stream()
                    .map(SendMessage::getText)
                    .filter(text -> text != null)
                    .filter(text -> text.contains("Клавиши быстрого доступа доступны"))
                    .count();

            assertEquals(1L, quickAccessMessages,
                    "Подсказка о быстрых клавишах должна отправляться единожды при повторном /start");
            assertFalse(chatSessionRepository.isKeyboardHidden(chatId),
                    "После повторного /start клавиатура должна считаться видимой");
        } finally {
            clearInvocations(telegramClient);
            doReturn(null).when(telegramClient).execute(any(SendMessage.class));
        }
    }

    /**
     * Проверяет, что при сохранённом однословном ФИО бот требует указать корректные данные.
     */
    @Test
    void shouldRequestFullNameWhenStoredNameInvalid() throws Exception {
        Long chatId = 1011L;
        Customer customer = new Customer();
        customer.setTelegramConfirmed(true);
        customer.setNameSource(NameSource.MERCHANT_PROVIDED);
        customer.setNotificationsEnabled(true);
        customer.setFullName("Иван");

        when(telegramService.findByChatId(chatId)).thenReturn(Optional.of(customer));

        bot.consume(textUpdate(chatId, "/start"));

        assertEquals(BuyerChatState.AWAITING_NAME_INPUT, bot.getState(chatId),
                "Бот должен запросить корректное ФИО вместо подтверждения");
        verify(telegramService).markNameUnconfirmed(chatId);
        verify(telegramService, never()).confirmName(chatId);

        ArgumentCaptor<SendMessage> captor = ArgumentCaptor.forClass(SendMessage.class);
        verify(telegramClient, atLeastOnce()).execute(captor.capture());
        boolean hasPrompt = captor.getAllValues().stream()
                .map(SendMessage::getText)
                .filter(text -> text != null)
                .anyMatch("Укажите своё ФИО"::equals);
        assertTrue(hasPrompt, "Пользователь должен получить подсказку указать своё ФИО");
    }

    /**
     * Убеждается, что после получения контакта без ФИО бот ждёт ввод имени.
     */
    @Test
    void shouldAwaitNameAfterContactWhenMissing() throws Exception {
        Long chatId = 1002L;
        Customer customer = new Customer();
        customer.setTelegramConfirmed(false);
        customer.setNameSource(NameSource.MERCHANT_PROVIDED);
        customer.setNotificationsEnabled(true);
        customer.setFullName(null);

        when(telegramService.linkTelegramToCustomer(anyString(), eq(chatId))).thenReturn(customer);
        when(telegramService.confirmTelegram(customer)).thenReturn(customer);

        bot.consume(contactUpdate(chatId, "+375291112233"));

        assertEquals(BuyerChatState.AWAITING_NAME_INPUT, bot.getState(chatId),
                "После контакта без имени бот должен ожидать ввод ФИО");
        verify(telegramService).confirmTelegram(customer);
        verify(telegramService).notifyActualStatuses(customer);

        ArgumentCaptor<SendMessage> captor = ArgumentCaptor.forClass(SendMessage.class);
        verify(telegramClient, atLeastOnce()).execute(captor.capture());
        boolean hasNamePrompt = captor.getAllValues().stream()
                .map(SendMessage::getText)
                .filter(text -> text != null)
                .anyMatch(text -> text.contains("укажите своё ФИО"));
        assertTrue(hasNamePrompt, "Пользователь должен получить просьбу указать ФИО");
    }

    /**
     * Проверяет, что ввод ФИО переводит сценарий в состояние ожидания команд.
     */
    @Test
    void shouldSwitchToIdleAfterNameInput() throws Exception {
        Long chatId = 1003L;
        Customer customer = new Customer();
        customer.setTelegramConfirmed(false);
        customer.setNameSource(NameSource.MERCHANT_PROVIDED);
        customer.setFullName(null);

        when(telegramService.linkTelegramToCustomer(anyString(), eq(chatId))).thenReturn(customer);
        when(telegramService.confirmTelegram(customer)).thenReturn(customer);

        bot.consume(contactUpdate(chatId, "+375297777777"));
        clearInvocations(telegramClient);

        when(telegramService.updateNameFromTelegram(chatId, "Иван Иванов")).thenAnswer(invocation -> {
            customer.setFullName("Иван Иванов");
            customer.setNameSource(NameSource.USER_CONFIRMED);
            return true;
        });
        when(telegramService.findByChatId(chatId)).thenReturn(Optional.of(customer));

        bot.consume(textUpdate(chatId, "  Иван Иванов  "));

        assertEquals(BuyerChatState.IDLE, bot.getState(chatId),
                "После ввода имени бот должен перейти в состояние IDLE");
        verify(telegramService).updateNameFromTelegram(chatId, "Иван Иванов");

        ArgumentCaptor<SendMessage> captor = ArgumentCaptor.forClass(SendMessage.class);
        verify(telegramClient, atLeastOnce()).execute(captor.capture());
        boolean hasSuccessMessage = captor.getAllValues().stream()
                .map(SendMessage::getText)
                .anyMatch("✅ ФИО сохранено и подтверждено"::equals);
        assertTrue(hasSuccessMessage, "Пользователь должен получить подтверждение сохранения ФИО");
    }

    /**
     * Убеждается, что неподходящая команда не выводит бота из ожидания контакта.
     */
    @Test
    void shouldStayAwaitingContactOnForeignCommand() throws Exception {
        Long chatId = 1004L;
        when(telegramService.findByChatId(chatId)).thenReturn(Optional.empty());

        bot.consume(textUpdate(chatId, "/start"));
        assertEquals(BuyerChatState.AWAITING_CONTACT, bot.getState(chatId));
        clearInvocations(telegramClient);

        bot.consume(textUpdate(chatId, "/stats"));

        assertEquals(BuyerChatState.AWAITING_CONTACT, bot.getState(chatId),
                "Бот должен продолжать ожидать контакт");
        ArgumentCaptor<SendMessage> captor = ArgumentCaptor.forClass(SendMessage.class);
        verify(telegramClient).execute(captor.capture());
        SendMessage message = captor.getValue();
        assertTrue(message.getText().contains("поделитесь контактом"),
                "Пользователь должен получить напоминание об отправке контакта");
        assertPhoneKeyboard(message);
        verify(telegramService, never()).getStatistics(chatId);
    }

    /**
     * Проверяет, что бот напоминает о необходимости ввести ФИО при неуместной команде.
     */
    @Test
    void shouldWarnAboutNameDuringUnexpectedCommand() throws Exception {
        Long chatId = 1005L;
        Customer customer = new Customer();
        customer.setTelegramConfirmed(false);
        customer.setNameSource(NameSource.MERCHANT_PROVIDED);
        customer.setFullName(null);

        when(telegramService.linkTelegramToCustomer(anyString(), eq(chatId))).thenReturn(customer);
        when(telegramService.confirmTelegram(customer)).thenReturn(customer);

        bot.consume(contactUpdate(chatId, "+375296666666"));
        assertEquals(BuyerChatState.AWAITING_NAME_INPUT, bot.getState(chatId));
        clearInvocations(telegramClient);

        bot.consume(textUpdate(chatId, "Верно"));

        assertEquals(BuyerChatState.AWAITING_NAME_INPUT, bot.getState(chatId),
                "Бот не должен завершать ожидание ФИО после неподходящей команды");
        ArgumentCaptor<SendMessage> captor = ArgumentCaptor.forClass(SendMessage.class);
        verify(telegramClient).execute(captor.capture());
        assertTrue(captor.getValue().getText().contains("ожидается ввод ФИО"),
                "Пользователь должен получить напоминание о вводе ФИО");
        verify(telegramService, never()).confirmName(chatId);
        verify(telegramService, never()).updateNameFromTelegram(anyLong(), anyString());
    }

    /**
     * Проверяет, что команда /menu возвращает диалог в состояние IDLE.
     */
    @Test
    void shouldResetStateToIdleOnMenuFromAnyState() throws Exception {
        Long chatId = 1006L;
        Customer customer = new Customer();
        customer.setTelegramConfirmed(false);
        customer.setNameSource(NameSource.MERCHANT_PROVIDED);
        customer.setFullName(null);

        when(telegramService.linkTelegramToCustomer(anyString(), eq(chatId))).thenReturn(customer);
        when(telegramService.confirmTelegram(customer)).thenReturn(customer);

        bot.consume(contactUpdate(chatId, "+375295555555"));
        assertEquals(BuyerChatState.AWAITING_NAME_INPUT, bot.getState(chatId));
        clearInvocations(telegramClient);

        chatSessionRepository.markKeyboardHidden(chatId);
        when(telegramService.findByChatId(chatId)).thenReturn(Optional.of(customer));

        bot.consume(textUpdate(chatId, "/menu"));

        assertEquals(BuyerChatState.IDLE, bot.getState(chatId),
                "Команда /menu должна переводить бот в состояние IDLE");
        ArgumentCaptor<SendMessage> captor = ArgumentCaptor.forClass(SendMessage.class);
        verify(telegramClient, atLeastOnce()).execute(captor.capture());
        var messages = captor.getAllValues();
        boolean hasMenuMessage = messages.stream()
                .map(SendMessage::getText)
                .filter(text -> text != null)
                .anyMatch(text -> text.contains("Главное меню"));
        assertTrue(hasMenuMessage, "Бот должен показать главное меню");
        assertMenuKeyboard(messages);
    }

    /**
     * Проверяет, что при вводе недопустимых символов бот остаётся в ожидании ФИО и показывает подсказку.
     */
    @Test
    void shouldStayAwaitingNameOnInvalidCharacters() throws Exception {
        Long chatId = 1007L;
        Customer customer = new Customer();
        customer.setTelegramConfirmed(false);
        customer.setNameSource(NameSource.MERCHANT_PROVIDED);
        customer.setFullName(null);

        when(telegramService.linkTelegramToCustomer(anyString(), eq(chatId))).thenReturn(customer);
        when(telegramService.confirmTelegram(customer)).thenReturn(customer);

        bot.consume(contactUpdate(chatId, "+375294444444"));
        assertEquals(BuyerChatState.AWAITING_NAME_INPUT, bot.getState(chatId));
        clearInvocations(telegramClient);

        bot.consume(textUpdate(chatId, "Иван123"));

        assertEquals(BuyerChatState.AWAITING_NAME_INPUT, bot.getState(chatId),
                "Состояние не должно меняться при некорректном вводе");
        verify(telegramService, never()).updateNameFromTelegram(anyLong(), anyString());

        ArgumentCaptor<SendMessage> captor = ArgumentCaptor.forClass(SendMessage.class);
        verify(telegramClient, atLeastOnce()).execute(captor.capture());
        boolean hasValidationMessage = captor.getAllValues().stream()
                .map(SendMessage::getText)
                .filter(text -> text != null)
                .anyMatch(text -> text.contains("буквы"));
        assertTrue(hasValidationMessage, "Пользователь должен получить подсказку о допустимых символах");
    }

    /**
     * Убеждается, что подтверждающая фраза «да» во время ожидания ФИО фиксирует существующее имя.
     */
    @Test
    void shouldConfirmExistingNameOnConfirmationPhraseWhileAwaiting() throws Exception {
        Long chatId = 1008L;
        Customer customer = new Customer();
        customer.setTelegramConfirmed(false);
        customer.setNameSource(NameSource.MERCHANT_PROVIDED);
        customer.setFullName("Иван Иванов");

        when(telegramService.linkTelegramToCustomer(anyString(), eq(chatId))).thenReturn(customer);
        when(telegramService.confirmTelegram(customer)).thenReturn(customer);
        when(telegramService.confirmName(chatId)).thenReturn(true);

        bot.consume(contactUpdate(chatId, "+375293333333"));
        assertEquals(BuyerChatState.AWAITING_NAME_INPUT, bot.getState(chatId));
        clearInvocations(telegramClient);

        bot.consume(textUpdate(chatId, "да"));

        assertEquals(BuyerChatState.IDLE, bot.getState(chatId),
                "После подтверждения бот должен вернуться в режим команд");
        verify(telegramService).confirmName(chatId);
        verify(telegramService, never()).updateNameFromTelegram(anyLong(), anyString());

        ArgumentCaptor<SendMessage> captor = ArgumentCaptor.forClass(SendMessage.class);
        verify(telegramClient, atLeastOnce()).execute(captor.capture());
        boolean hasSuccessMessage = captor.getAllValues().stream()
                .map(SendMessage::getText)
                .filter(text -> text != null)
                .anyMatch(text -> text.contains("данные подтверждены"));
        assertTrue(hasSuccessMessage, "Пользователь должен получить подтверждение успешного ответа");
    }

    /**
     * Проверяет, что при подтверждающей фразе без сохранённого имени бот просит указать ФИО.
     */
    @Test
    void shouldWarnWhenConfirmationPhraseWithoutStoredName() throws Exception {
        Long chatId = 1009L;
        Customer customer = new Customer();
        customer.setTelegramConfirmed(false);
        customer.setNameSource(NameSource.MERCHANT_PROVIDED);
        customer.setFullName(null);

        when(telegramService.linkTelegramToCustomer(anyString(), eq(chatId))).thenReturn(customer);
        when(telegramService.confirmTelegram(customer)).thenReturn(customer);
        when(telegramService.confirmName(chatId)).thenReturn(false);

        bot.consume(contactUpdate(chatId, "+375292222222"));
        assertEquals(BuyerChatState.AWAITING_NAME_INPUT, bot.getState(chatId));
        clearInvocations(telegramClient);

        bot.consume(textUpdate(chatId, "ок"));

        assertEquals(BuyerChatState.AWAITING_NAME_INPUT, bot.getState(chatId),
                "Имя остаётся неподтверждённым, бот продолжает ожидать ввод");
        verify(telegramService).confirmName(chatId);
        verify(telegramService, never()).updateNameFromTelegram(anyLong(), anyString());

        ArgumentCaptor<SendMessage> captor = ArgumentCaptor.forClass(SendMessage.class);
        verify(telegramClient, atLeastOnce()).execute(captor.capture());
        boolean hasReminder = captor.getAllValues().stream()
                .map(SendMessage::getText)
                .filter(text -> text != null)
                .anyMatch(text -> text.contains("Пожалуйста, укажите его полностью"));
        assertTrue(hasReminder, "Бот должен подсказать о необходимости ввести ФИО");
    }

    /**
     * Убеждается, что в режиме меню ответ «верно» также подтверждает имя без повторного ввода.
     */
    @Test
    void shouldConfirmNameInIdleOnConfirmationPhrase() throws Exception {
        Long chatId = 1010L;
        Customer customer = new Customer();
        customer.setTelegramConfirmed(true);
        customer.setNameSource(NameSource.MERCHANT_PROVIDED);
        customer.setFullName("Мария Петрова");

        when(telegramService.findByChatId(chatId)).thenReturn(Optional.of(customer));
        when(telegramService.confirmName(chatId)).thenReturn(true);

        bot.consume(textUpdate(chatId, "/start"));
        clearInvocations(telegramClient);

        bot.consume(textUpdate(chatId, "Верно"));

        assertEquals(BuyerChatState.IDLE, bot.getState(chatId),
                "Подтверждение не должно менять состояние меню");
        verify(telegramService).confirmName(chatId);
        verify(telegramService, never()).updateNameFromTelegram(anyLong(), anyString());

        ArgumentCaptor<SendMessage> captor = ArgumentCaptor.forClass(SendMessage.class);
        verify(telegramClient, atLeastOnce()).execute(captor.capture());
        boolean hasSuccess = captor.getAllValues().stream()
                .map(SendMessage::getText)
                .filter(text -> text != null)
                .anyMatch(text -> text.contains("данные подтверждены"));
        assertTrue(hasSuccess, "Пользователь должен получить сообщение об успешном подтверждении");
    }

    /**
     * Проверяет, что команда /start заново отправляет клавиатуру меню, даже если флаг скрытия сброшен вручную.
     */
    @Test
    void shouldResendReplyKeyboardOnStartWhenFlagReset() throws Exception {
        Long chatId = 3031L;
        Customer customer = new Customer();
        customer.setTelegramConfirmed(true);
        customer.setNameSource(NameSource.USER_CONFIRMED);
        customer.setNotificationsEnabled(true);
        customer.setFullName("Анна Смирнова");

        when(telegramService.findByChatId(chatId)).thenReturn(Optional.of(customer));

        chatSessionRepository.markKeyboardVisible(chatId);

        bot.consume(textUpdate(chatId, "/start"));

        ArgumentCaptor<SendMessage> captor = ArgumentCaptor.forClass(SendMessage.class);
        verify(telegramClient, atLeastOnce()).execute(captor.capture());

        boolean hasMenuKeyboard = captor.getAllValues().stream()
                .map(SendMessage::getReplyMarkup)
                .filter(ReplyKeyboardMarkup.class::isInstance)
                .map(ReplyKeyboardMarkup.class::cast)
                .anyMatch(this::containsOnlyMenuButton);

        assertTrue(hasMenuKeyboard,
                "После повторной команды /start бот обязан вернуть клавиатуру с кнопкой «🏠 Меню»");
    }

    /**
     * Убеждается, что при неизменном тексте главного меню бот не создаёт новое якорное сообщение.
     * <p>Дополнительно проверяется отсутствие повторной записи о главном меню в логах.</p>
     */
    @Test
    void shouldKeepAnchorWhenMenuMessageUnchanged() throws Exception {
        Long chatId = 4040L;
        Customer customer = new Customer();
        customer.setTelegramConfirmed(true);
        customer.setNameSource(NameSource.USER_CONFIRMED);
        customer.setNotificationsEnabled(true);
        customer.setFullName("Иван Иванов");

        when(telegramService.findByChatId(chatId)).thenReturn(Optional.of(customer));

        AtomicInteger messageIdSequence = new AtomicInteger(500);
        lenient().when(telegramClient.execute(any(SendMessage.class))).thenAnswer(invocation -> {
            Message message = new Message();
            message.setMessageId(messageIdSequence.incrementAndGet());
            return message;
        });

        lenient().doAnswer(invocation -> {
            throw new TelegramApiException("Bad Request: message is not modified");
        }).when(telegramClient).execute(any(EditMessageText.class));

        try {
            bot.consume(textUpdate(chatId, "/start"));

            Integer initialAnchor = chatSessionRepository.find(chatId)
                    .map(ChatSession::getAnchorMessageId)
                    .orElseThrow(() -> new AssertionError("После старта должен сохраниться якорь главного меню"));

            clearInvocations(telegramClient);

            Logger logger = (Logger) LoggerFactory.getLogger(BuyerTelegramBot.class);
            ListAppender<ILoggingEvent> appender = new ListAppender<>();
            appender.start();
            logger.addAppender(appender);

            List<ILoggingEvent> logEvents = List.of();
            try {
                bot.consume(textUpdate(chatId, "/start"));
                logEvents = List.copyOf(appender.list);
            } finally {
                logger.detachAppender(appender);
                appender.stop();
            }

            ChatSession session = chatSessionRepository.find(chatId)
                    .orElseThrow(() -> new AssertionError("Сессия должна существовать после повторного запуска"));

            assertEquals(initialAnchor, session.getAnchorMessageId(),
                    "Бот не должен отправлять новое якорное сообщение при неизменном тексте");
            assertEquals(BuyerBotScreen.MENU, session.getLastScreen(),
                    "Последний экран обязан оставаться главным меню после повторного запуска");
            assertFalse(chatSessionRepository.isKeyboardHidden(chatId),
                    "После повторной команды /start клавиатура должна считаться видимой");

            verify(telegramClient).execute(any(EditMessageText.class));

            ArgumentCaptor<SendMessage> captor = ArgumentCaptor.forClass(SendMessage.class);
            verify(telegramClient, atMost(1)).execute(captor.capture());
            List<SendMessage> messages = captor.getAllValues();
            boolean hasMainMenuMessage = messages.stream()
                    .anyMatch(this::isMainMenuAnchorMessage);
            assertFalse(hasMainMenuMessage,
                    "Повторная команда /start не должна создавать новое сообщение «Главное меню»");

            boolean hasLogAboutMainMenu = logEvents.stream()
                    .map(ILoggingEvent::getFormattedMessage)
                    .filter(message -> message != null)
                    .anyMatch(message -> message.toLowerCase().contains("главн"));
            assertFalse(hasLogAboutMainMenu,
                    "Логи повторного /start не должны содержать записи о новом главном меню");
        } finally {
            doReturn(null).when(telegramClient).execute(any(EditMessageText.class));
            doReturn(null).when(telegramClient).execute(any(SendMessage.class));
        }
    }

    /**
     * Проверяет, что при потере якоря бот повторно отправляет постоянную клавиатуру.
     */
    @Test
    void shouldResendReplyKeyboardAfterAnchorCleared() throws Exception {
        Long chatId = 2020L;
        Customer customer = new Customer();
        customer.setTelegramConfirmed(true);
        customer.setNameSource(NameSource.USER_CONFIRMED);
        customer.setFullName("Иван Иванов");

        when(telegramService.findByChatId(chatId)).thenReturn(Optional.of(customer));

        chatSessionRepository.markKeyboardVisible(chatId);

        bot.consume(textUpdate(chatId, "/start"));

        clearInvocations(telegramClient);

        chatSessionRepository.clearAnchor(chatId);

        assertTrue(chatSessionRepository.isKeyboardHidden(chatId),
                "После потери якоря клавиатура должна помечаться как скрытая");

        bot.consume(textUpdate(chatId, "/menu"));

        ArgumentCaptor<SendMessage> captor = ArgumentCaptor.forClass(SendMessage.class);
        verify(telegramClient, atLeastOnce()).execute(captor.capture());

        boolean hasReplyKeyboard = captor.getAllValues().stream()
                .map(SendMessage::getReplyMarkup)
                .anyMatch(ReplyKeyboardMarkup.class::isInstance);

        assertTrue(hasReplyKeyboard,
                "Повторный показ меню обязан переотправить reply-клавиатуру");
    }

    /**
     * Проверяет, что повторное нажатие кнопки «🏠 Меню» приводит к созданию нового сообщения
     * главного меню, а у старого сообщения исчезают инлайн-кнопки.
     */
    @Test
    void shouldRefreshMenuMessageWhenMenuButtonPressedTwice() throws Exception {
        Long chatId = 9090L;
        int previousAnchorId = 777;

        Customer customer = new Customer();
        customer.setTelegramConfirmed(true);
        customer.setNotificationsEnabled(true);
        customer.setNameSource(NameSource.USER_CONFIRMED);
        customer.setFullName("Иван Иванов");

        when(telegramService.findByChatId(chatId)).thenReturn(Optional.of(customer));

        chatSessionRepository.updateAnchorAndScreen(chatId,
                previousAnchorId,
                BuyerBotScreen.MENU,
                List.of(BuyerBotScreen.MENU));
        chatSessionRepository.markKeyboardVisible(chatId);

        AtomicInteger messageIdSequence = new AtomicInteger(500);
        AtomicInteger newMenuAnchorId = new AtomicInteger();

        try {
            when(telegramClient.execute(any(SendMessage.class))).thenAnswer(invocation -> {
                SendMessage request = invocation.getArgument(0);
                Message sent = new Message();
                int assignedId = messageIdSequence.incrementAndGet();
                sent.setMessageId(assignedId);
                if (request.getText() != null && request.getText().contains("Главное меню")) {
                    newMenuAnchorId.set(assignedId);
                }
                return sent;
            });

            bot.consume(textUpdate(chatId, "🏠 Меню"));

            ArgumentCaptor<EditMessageReplyMarkup> markupCaptor = ArgumentCaptor.forClass(EditMessageReplyMarkup.class);
            verify(telegramClient, atLeastOnce()).execute(markupCaptor.capture());
            boolean keyboardDetached = markupCaptor.getAllValues().stream()
                    .filter(Objects::nonNull)
                    .anyMatch(request -> Objects.equals(request.getMessageId(), previousAnchorId)
                            && request.getReplyMarkup() == null);

            assertTrue(keyboardDetached,
                    "Старое сообщение меню должно лишиться инлайн-кнопок после повторного нажатия");

            ArgumentCaptor<SendMessage> messageCaptor = ArgumentCaptor.forClass(SendMessage.class);
            verify(telegramClient, atLeastOnce()).execute(messageCaptor.capture());
            boolean hasMenuMessage = messageCaptor.getAllValues().stream()
                    .map(SendMessage::getText)
                    .filter(Objects::nonNull)
                    .anyMatch(text -> text.contains("Главное меню"));

            assertTrue(hasMenuMessage,
                    "При повторном нажатии кнопки «🏠 Меню» бот обязан отправить новое сообщение меню");

            ChatSession session = chatSessionRepository.find(chatId)
                    .orElseThrow(() -> new AssertionError("Данные сессии должны сохраняться после повторного меню"));

            assertEquals(BuyerBotScreen.MENU, session.getLastScreen(),
                    "Последний экран должен оставаться главным меню");
            assertNotNull(session.getAnchorMessageId(),
                    "После переотправки меню должен быть зафиксирован новый идентификатор сообщения");
            assertNotEquals(previousAnchorId, session.getAnchorMessageId(),
                    "Новый якорь меню не может совпадать со старым идентификатором");
            assertEquals(newMenuAnchorId.get(), session.getAnchorMessageId(),
                    "В сессии должен сохраниться идентификатор нового сообщения меню");

            verify(telegramClient, never()).execute(any(EditMessageText.class));
        } finally {
            clearInvocations(telegramClient);
            doReturn(null).when(telegramClient).execute(any(SendMessage.class));
        }
    }

    /**
     * Создаёт обновление типа my_chat_member для сценария онбординга.
     *
     * @param chatId идентификатор чата Telegram
     * @return объект {@link Update} с заполненными данными чата
     */
    private Update myChatMemberUpdate(Long chatId) {
        ChatMemberUpdated myChatMember = new ChatMemberUpdated();
        myChatMember.setChat(createChat(chatId));

        Update update = new Update();
        update.setMyChatMember(myChatMember);
        return update;
    }

    /**
     * Создаёт обновление Telegram с текстовым сообщением пользователя.
     *
     * @param chatId идентификатор чата Telegram
     * @param text   текст, который отправил пользователь
     * @return объект {@link Update} для передачи в бота
     */
    private Update textUpdate(Long chatId, String text) {
        Message message = new Message();
        message.setMessageId(1);
        message.setText(text);
        message.setChat(createChat(chatId));

        Update update = new Update();
        update.setMessage(message);
        return update;
    }

    /**
     * Создаёт обновление с контактом пользователя.
     *
     * @param chatId идентификатор чата Telegram
     * @param phone  номер телефона, переданный пользователем
     * @return объект {@link Update} с заполненным контактом
     */
    private Update contactUpdate(Long chatId, String phone) {
        Message message = new Message();
        message.setMessageId(1);
        message.setChat(createChat(chatId));
        Contact contact = new Contact();
        contact.setPhoneNumber(phone);
        contact.setUserId(chatId);
        message.setContact(contact);

        Update update = new Update();
        update.setMessage(message);
        return update;
    }

    /**
     * Создаёт callback-запрос от пользователя с заданным сообщением и данными.
     *
     * @param chatId    идентификатор чата Telegram
     * @param messageId идентификатор сообщения, с которого пришёл callback
     * @param data      полезная нагрузка callback-запроса
     * @return объект {@link Update} для передачи в бота
     */
    private Update callbackUpdate(Long chatId, Integer messageId, String data) {
        Message message = new Message();
        message.setMessageId(messageId);
        message.setChat(createChat(chatId));

        CallbackQuery callbackQuery = new CallbackQuery();
        callbackQuery.setId("cb-" + messageId);
        callbackQuery.setMessage(message);
        callbackQuery.setData(data);

        Update update = new Update();
        update.setCallbackQuery(callbackQuery);
        return update;
    }

    /**
     * Создаёт объект чата Telegram для обновления.
     *
     * @param chatId идентификатор чата
     * @return объект {@link Chat} с заданным идентификатором
     */
    private Chat createChat(Long chatId) {
        return Chat.builder()
                .id(chatId)
                .type("private")
                .build();
    }

    /**
     * Проверяет, что сообщение содержит клавиатуру с запросом контакта.
     *
     * @param message отправленное ботом сообщение
     */
    private void assertPhoneKeyboard(SendMessage message) {
        assertNotNull(message, "Сообщение не должно быть null");
        assertNotNull(message.getReplyMarkup(), "Ожидалась клавиатура с запросом контакта");
        assertTrue(message.getReplyMarkup() instanceof ReplyKeyboardMarkup,
                "Клавиатура должна быть типа ReplyKeyboardMarkup");

        ReplyKeyboardMarkup markup = (ReplyKeyboardMarkup) message.getReplyMarkup();
        List<KeyboardRow> rows = markup.getKeyboard();
        assertNotNull(rows, "Список строк клавиатуры не должен быть пустым");
        assertFalse(rows.isEmpty(), "Клавиатура должна содержать хотя бы одну строку");

        boolean hasContactButton = false;
        for (KeyboardRow row : rows) {
            for (KeyboardButton button : row) {
                if (Boolean.TRUE.equals(button.getRequestContact())) {
                    hasContactButton = true;
                    break;
                }
            }
            if (hasContactButton) {
                break;
            }
        }

        assertTrue(hasContactButton, "Кнопка запроса контакта должна присутствовать");
    }

    /**
     * Проверяет, содержит ли инлайн-клавиатура кнопки главного меню.
     *
     * @param markup инлайн-клавиатура, отправленная пользователю
     * @return {@code true}, если присутствуют кнопки «📊 Статистика», «⚙️ Настройки» и «❓ Помощь»
     */
    private boolean containsMenuInlineButtons(InlineKeyboardMarkup markup) {
        if (markup == null || markup.getKeyboard() == null) {
            return false;
        }

        boolean hasStats = false;
        boolean hasSettings = false;
        boolean hasHelp = false;

        for (InlineKeyboardRow row : markup.getKeyboard()) {
            if (row == null) {
                continue;
            }
            for (InlineKeyboardButton button : row) {
                if (button == null) {
                    continue;
                }
                String text = button.getText();
                if ("📊 Статистика".equals(text)) {
                    hasStats = true;
                }
                if ("⚙️ Настройки".equals(text)) {
                    hasSettings = true;
                }
                if ("❓ Помощь".equals(text)) {
                    hasHelp = true;
                }
            }
        }

        return hasStats && hasSettings && hasHelp;
    }

    /**
     * Определяет, относится ли сообщение к якорному главному меню.
     *
     * @param message сообщение, отправленное ботом
     * @return {@code true}, если сообщение содержит текст или инлайн-кнопки главного меню
     */
    private boolean isMainMenuAnchorMessage(SendMessage message) {
        if (message == null) {
            return false;
        }

        String text = message.getText();
        if (text != null && text.contains("Главное меню")) {
            return true;
        }

        if (message.getReplyMarkup() instanceof InlineKeyboardMarkup inlineMarkup) {
            return containsMenuInlineButtons(inlineMarkup);
        }

        return false;
    }

    /**
     * Проверяет наличие клавиатуры меню среди отправленных сообщений.
     *
     * @param messages сообщения, отправленные ботом в рамках сценария
     */
    private void assertMenuKeyboard(List<SendMessage> messages) {
        boolean hasKeyboard = messages.stream()
                .map(SendMessage::getReplyMarkup)
                .filter(ReplyKeyboardMarkup.class::isInstance)
                .map(ReplyKeyboardMarkup.class::cast)
                .anyMatch(this::containsOnlyMenuButton);
        assertTrue(hasKeyboard,
                "После скрытия клавиатуры бот обязан вернуть кнопку «🏠 Меню»");
    }

    /**
     * Проверяет, содержит ли клавиатура только кнопку меню.
     *
     * @param markup проверяемая клавиатура
     * @return {@code true}, если единственная активная кнопка — «🏠 Меню»
     */
    private boolean containsOnlyMenuButton(ReplyKeyboardMarkup markup) {
        if (markup == null || markup.getKeyboard() == null) {
            return false;
        }

        boolean hasMenu = false;
        for (KeyboardRow row : markup.getKeyboard()) {
            if (row == null) {
                continue;
            }
            for (KeyboardButton button : row) {
                if (button == null) {
                    continue;
                }
                String text = button.getText();
                if ("🏠 Меню".equals(text)) {
                    if (hasMenu) {
                        return false;
                    }
                    hasMenu = true;
                    continue;
                }
                if (text != null && !text.isBlank()) {
                    return false;
                }
            }
        }
        return hasMenu;
    }

    /**
     * Проверяет, содержит ли клавиатура кнопку запроса контакта.
     *
     * @param markup проверяемая клавиатура
     * @return {@code true}, если присутствует кнопка «📱 Поделиться номером» или кнопка запроса контакта
     */
    private boolean containsContactButton(ReplyKeyboardMarkup markup) {
        if (markup == null || markup.getKeyboard() == null) {
            return false;
        }

        for (KeyboardRow row : markup.getKeyboard()) {
            if (row == null) {
                continue;
            }
            for (KeyboardButton button : row) {
                if (button == null) {
                    continue;
                }
                if (Boolean.TRUE.equals(button.getRequestContact())) {
                    return true;
                }
                if ("📱 Поделиться номером".equals(button.getText())) {
                    return true;
                }
            }
        }
        return false;
    }
}
