package com.project.tracking_system.service.telegram;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.tracking_system.entity.BuyerBotScreen;
import com.project.tracking_system.entity.BuyerChatState;
import com.project.tracking_system.entity.Customer;
import com.project.tracking_system.entity.NameSource;
import com.project.tracking_system.service.admin.AdminNotificationService;
import com.project.tracking_system.service.customer.CustomerTelegramService;
import com.project.tracking_system.service.telegram.support.InMemoryChatSessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageReplyMarkup;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.chat.Chat;
import org.telegram.telegrambots.meta.api.objects.Contact;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.message.MaybeInaccessibleMessage;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Проверяет восстановление состояния бота после «рестарта» (создания нового экземпляра).
 */
class BuyerTelegramBotStatePersistenceIntegrationTest {

    private InMemoryChatSessionRepository chatSessionRepository;
    private FullNameValidator fullNameValidator;

    @BeforeEach
    void setUp() {
        chatSessionRepository = new InMemoryChatSessionRepository();
        fullNameValidator = new FullNameValidator();
    }

    /**
     * После получения контакта и ожидания ФИО бот должен продолжить сценарий после рестарта.
     */
    @Test
    void shouldRestoreAwaitingNameAfterRestart() throws Exception {
        Long chatId = 2001L;
        TelegramClient telegramClient = mock(TelegramClient.class);
        CustomerTelegramService telegramService = mock(CustomerTelegramService.class);
        AdminNotificationService adminNotificationService = mock(AdminNotificationService.class);
        when(telegramClient.execute(any(SendMessage.class))).thenReturn(null);
        when(adminNotificationService.findActiveNotification()).thenReturn(Optional.empty());

        Customer customer = new Customer();
        customer.setTelegramConfirmed(false);
        customer.setNameSource(NameSource.MERCHANT_PROVIDED);
        customer.setNotificationsEnabled(true);
        customer.setFullName(null);

        when(telegramService.linkTelegramToCustomer(anyString(), eq(chatId))).thenReturn(customer);
        when(telegramService.confirmTelegram(customer)).thenReturn(customer);
        doNothing().when(telegramService).notifyActualStatuses(customer);

        BuyerTelegramBot bot = new BuyerTelegramBot(telegramClient, "token", telegramService, adminNotificationService,
                fullNameValidator, chatSessionRepository, new ObjectMapper());

        bot.consume(contactUpdate(chatId, "+375291112233"));

        assertEquals(BuyerChatState.AWAITING_NAME_INPUT, chatSessionRepository.getState(chatId),
                "Состояние ожидания ФИО должно сохраниться в хранилище");

        TelegramClient restartedClient = mock(TelegramClient.class);
        when(restartedClient.execute(any(SendMessage.class))).thenReturn(null);
        when(adminNotificationService.findActiveNotification()).thenReturn(Optional.empty());
        BuyerTelegramBot restartedBot = new BuyerTelegramBot(restartedClient, "token", telegramService, adminNotificationService,
                fullNameValidator, chatSessionRepository, new ObjectMapper());

        clearInvocations(telegramService);
        when(telegramService.updateNameFromTelegram(chatId, "Иван Иванов")).thenAnswer(invocation -> {
            customer.setFullName("Иван Иванов");
            customer.setNameSource(NameSource.USER_CONFIRMED);
            return true;
        });
        when(telegramService.findByChatId(chatId)).thenReturn(Optional.of(customer));

        restartedBot.consume(textUpdate(chatId, "Иван Иванов"));

        assertEquals(BuyerChatState.IDLE, chatSessionRepository.getState(chatId),
                "После ввода имени бот должен вернуться в режим команд");
        verify(telegramService).updateNameFromTelegram(chatId, "Иван Иванов");
    }

    /**
     * Проверяет, что данные якорного сообщения используются новым экземпляром бота для отрисовки экрана.
     */
    @Test
    void shouldReuseStoredScreenAfterRestart() throws Exception {
        Long chatId = 2002L;
        TelegramClient initialClient = mock(TelegramClient.class);
        CustomerTelegramService telegramService = mock(CustomerTelegramService.class);
        AdminNotificationService adminNotificationService = mock(AdminNotificationService.class);

        Message menuMessage = new Message();
        menuMessage.setMessageId(555);
        when(initialClient.execute(any(SendMessage.class))).thenReturn(menuMessage);
        when(adminNotificationService.findActiveNotification()).thenReturn(Optional.empty());

        Customer customer = new Customer();
        customer.setTelegramConfirmed(true);
        customer.setNotificationsEnabled(true);
        customer.setFullName("Иван Иванов");
        customer.setNameSource(NameSource.USER_CONFIRMED);
        when(telegramService.findByChatId(chatId)).thenReturn(Optional.of(customer));

        BuyerTelegramBot bot = new BuyerTelegramBot(initialClient, "token", telegramService, adminNotificationService,
                fullNameValidator, chatSessionRepository, new ObjectMapper());
        bot.consume(textUpdate(chatId, "/start"));

        ChatSession savedSession = chatSessionRepository.find(chatId).orElse(null);
        assertNotNull(savedSession, "После /start должна быть сохранена сессия");
        assertEquals(555, savedSession.getAnchorMessageId(), "Якорное сообщение должно быть сохранено");
        assertEquals(BuyerBotScreen.MENU, savedSession.getLastScreen(),
                "Последний экран должен соответствовать главному меню");

        TelegramClient restartedClient = mock(TelegramClient.class);
        when(restartedClient.execute(any(AnswerCallbackQuery.class))).thenReturn(null);
        when(restartedClient.execute(any(EditMessageReplyMarkup.class))).thenReturn(null);
        when(restartedClient.execute(any(EditMessageText.class))).thenReturn(null);

        when(adminNotificationService.findActiveNotification()).thenReturn(Optional.empty());
        BuyerTelegramBot restartedBot = new BuyerTelegramBot(restartedClient, "token", telegramService, adminNotificationService,
                fullNameValidator, chatSessionRepository, new ObjectMapper());

        CallbackQuery callbackQuery = mock(CallbackQuery.class);
        MaybeInaccessibleMessage callbackMessage = mock(MaybeInaccessibleMessage.class);
        when(callbackQuery.getMessage()).thenReturn(callbackMessage);
        when(callbackQuery.getData()).thenReturn("menu:stats");
        when(callbackQuery.getId()).thenReturn("cb1");
        when(callbackMessage.getChatId()).thenReturn(chatId);
        when(callbackMessage.getMessageId()).thenReturn(554); // устаревшее сообщение

        Update update = mock(Update.class);
        when(update.hasCallbackQuery()).thenReturn(true);
        when(update.getCallbackQuery()).thenReturn(callbackQuery);

        restartedBot.consume(update);

        ArgumentCaptor<EditMessageText> editCaptor = ArgumentCaptor.forClass(EditMessageText.class);
        verify(restartedClient).execute(editCaptor.capture());
        assertEquals(555, editCaptor.getValue().getMessageId(),
                "Должно редактироваться сохранённое якорное сообщение");
    }

    private Update textUpdate(Long chatId, String text) {
        Message message = new Message();
        message.setMessageId(1);
        message.setText(text);
        message.setChat(createChat(chatId));

        Update update = new Update();
        update.setMessage(message);
        return update;
    }

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

    private Chat createChat(Long chatId) {
        return Chat.builder()
                .id(chatId)
                .type("private")
                .build();
    }
}
