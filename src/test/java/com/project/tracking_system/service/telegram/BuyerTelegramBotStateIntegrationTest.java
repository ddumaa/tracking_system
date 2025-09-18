package com.project.tracking_system.service.telegram;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.tracking_system.entity.BuyerBotScreen;
import com.project.tracking_system.entity.BuyerChatState;
import com.project.tracking_system.entity.Customer;
import com.project.tracking_system.entity.NameSource;
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
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageReplyMarkup;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.Chat;
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
import org.telegram.telegrambots.meta.generics.TelegramClient;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

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

    private BuyerTelegramBot bot;
    private FullNameValidator fullNameValidator;
    private InMemoryChatSessionRepository chatSessionRepository;

    /**
     * Создаёт экземпляр бота перед каждым сценарием и стабилизирует клиент Telegram.
     */
    @BeforeEach
    void setUp() throws Exception {
        fullNameValidator = new FullNameValidator();
        chatSessionRepository = new InMemoryChatSessionRepository();
        bot = new BuyerTelegramBot(telegramClient, "token", telegramService, fullNameValidator, chatSessionRepository,
                new ObjectMapper());
        when(telegramClient.execute(any(SendMessage.class))).thenReturn(null);
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

        chatSessionRepository.updateAnchorAndScreen(chatId, anchorId, BuyerBotScreen.MENU);
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
        User user = new User();
        user.setId(chatId);
        update.getMessage().setFrom(user);

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
        when(telegramClient.execute(any(SendMessage.class))).thenAnswer(invocation -> {
            Message message = new Message();
            message.setMessageId(messageIdSequence.incrementAndGet());
            return message;
        });

        doAnswer(invocation -> {
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

        chatSessionRepository.updateAnchorAndScreen(chatId, previousAnchorId, BuyerBotScreen.MENU);
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
     * Создаёт объект чата Telegram для обновления.
     *
     * @param chatId идентификатор чата
     * @return объект {@link Chat} с заданным идентификатором
     */
    private Chat createChat(Long chatId) {
        Chat chat = new Chat();
        chat.setId(chatId);
        chat.setType("private");
        return chat;
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
