package com.project.tracking_system.service.telegram;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.project.tracking_system.dto.TelegramParcelInfoDTO;
import com.project.tracking_system.dto.TelegramParcelsOverviewDTO;
import com.project.tracking_system.entity.AdminNotification;
import com.project.tracking_system.entity.BuyerBotScreen;
import com.project.tracking_system.entity.BuyerChatState;
import com.project.tracking_system.entity.Customer;
import com.project.tracking_system.entity.NameSource;
import com.project.tracking_system.entity.GlobalStatus;
import com.project.tracking_system.service.admin.AdminNotificationService;
import com.project.tracking_system.service.customer.CustomerTelegramService;
import com.project.tracking_system.utils.PhoneUtils;
import com.project.tracking_system.service.telegram.support.InMemoryChatSessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.ParseMode;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.chat.Chat;
import org.telegram.telegrambots.meta.api.objects.Contact;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboard;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;
import org.telegram.telegrambots.meta.api.objects.chatmember.ChatMemberUpdated;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
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
        try {
            doAnswer(invocation -> {
                Message response = new Message();
                response.setMessageId(messageIdSequence.getAndIncrement());
                return response;
            }).when(telegramClient).execute(any(SendMessage.class));
        } catch (TelegramApiException e) {
            throw new RuntimeException(e);
        }
        try {
            when(telegramClient.execute(any(EditMessageText.class))).thenReturn(null);
        } catch (TelegramApiException e) {
            throw new RuntimeException(e);
        }
        try {
            when(telegramClient.execute(any(AnswerCallbackQuery.class))).thenReturn(null);
        } catch (TelegramApiException e) {
            throw new RuntimeException(e);
        }
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
        customer.setTelegramConfirmed(true);
        customer.setNotificationsEnabled(true);
        customer.setFullName("Иван Иванов");
        customer.setNameSource(NameSource.USER_CONFIRMED);

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

        assertEquals(ParseMode.MARKDOWN, bannerEdit.getParseMode(),
                "Баннер объявления должен отправляться с включённым Markdown для корректной разметки");
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
        assertEquals(notification.getUpdatedAt(), session.getAnnouncementUpdatedAt(),
                "В сессии должно сохраняться время обновления объявления");
    }

    /**
     * Проверяет, что баннер объявления отображается для подтверждённого покупателя.
     */
    @Test
    void shouldRenderAnnouncementForConfirmedCustomer() throws Exception {
        Long chatId = 779L;
        Customer customer = new Customer();
        customer.setTelegramChatId(chatId);
        customer.setTelegramConfirmed(true);
        customer.setNotificationsEnabled(true);
        customer.setFullName("Пётр Петров");
        customer.setNameSource(NameSource.USER_CONFIRMED);

        when(telegramService.findByChatId(chatId)).thenReturn(Optional.of(customer));

        AdminNotification notification = new AdminNotification();
        notification.setId(43L);
        notification.setTitle("Свежая новость");
        notification.setBodyLines(List.of("Пункт один"));
        notification.setUpdatedAt(ZonedDateTime.now().minusMinutes(1));
        when(adminNotificationService.findActiveNotification()).thenReturn(Optional.of(notification));

        bot.consume(mockTextUpdate(chatId, "/start"));

        ArgumentCaptor<EditMessageText> editCaptor = ArgumentCaptor.forClass(EditMessageText.class);
        verify(telegramClient, atLeastOnce()).execute(editCaptor.capture());
        boolean bannerRendered = editCaptor.getAllValues().stream()
                .anyMatch(edit -> edit.getText() != null && edit.getText().contains(notification.getTitle()));

        assertTrue(bannerRendered,
                "Баннер объявления должен отображаться для подтверждённых покупателей");

        ChatSession session = chatSessionRepository.find(chatId)
                .orElseThrow(() -> new AssertionError("Сессия должна сохраняться для отображения объявления"));
        assertEquals(notification.getId(), session.getCurrentNotificationId(),
                "Объявление должно фиксироваться в состоянии сессии для дальнейшего контроля показов");
        assertEquals(notification.getUpdatedAt(), session.getAnnouncementUpdatedAt(),
                "В состоянии сессии должно храниться время обновления объявления");
    }

    /**
     * Проверяет, что обновлённое объявление с тем же идентификатором снова показывается пользователю.
     */
    @Test
    void shouldResendAnnouncementWhenContentRefreshed() throws Exception {
        Long chatId = 780L;
        Customer customer = new Customer();
        customer.setTelegramChatId(chatId);
        customer.setTelegramConfirmed(true);
        customer.setNotificationsEnabled(true);
        when(telegramService.findByChatId(chatId)).thenReturn(Optional.of(customer));

        AdminNotification notification = new AdminNotification();
        notification.setId(44L);
        notification.setTitle("Объявление для обновления");
        notification.setBodyLines(List.of("Первая версия"));
        ZonedDateTime initialUpdatedAt = ZonedDateTime.now().minusMinutes(15);
        notification.setUpdatedAt(initialUpdatedAt);
        when(adminNotificationService.findActiveNotification()).thenReturn(Optional.of(notification));

        bot.consume(mockTextUpdate(chatId, "/start"));

        chatSessionRepository.markAnnouncementSeen(chatId);
        clearInvocations(telegramClient);

        ZonedDateTime refreshedAt = initialUpdatedAt.plusMinutes(5);
        notification.setUpdatedAt(refreshedAt);
        notification.setBodyLines(List.of("Обновлённая версия"));

        bot.consume(mockTextUpdate(chatId, "/start"));

        ArgumentCaptor<EditMessageText> editCaptor = ArgumentCaptor.forClass(EditMessageText.class);
        verify(telegramClient, atLeastOnce()).execute(editCaptor.capture());
        boolean bannerUpdated = editCaptor.getAllValues().stream()
                .map(EditMessageText::getText)
                .filter(Objects::nonNull)
                .anyMatch(text -> text.contains("Обновлённая версия"));

        assertTrue(bannerUpdated,
                "После обновления содержимого баннер должен быть переотправлен пользователю");

        ChatSession session = chatSessionRepository.find(chatId)
                .orElseThrow(() -> new AssertionError("Состояние сессии должно быть сохранено"));
        assertEquals(refreshedAt, session.getAnnouncementUpdatedAt(),
                "В сессии должна храниться новая отметка обновления объявления");
        assertFalse(session.isAnnouncementSeen(),
                "После обновления содержимого признак просмотра должен быть сброшен");
    }

    /**
     * Проверяет, что список посылок группируется по магазину и выводит только трек-номера.
     */
    @Test
    void shouldGroupParcelsByStoreWithTracksOnly() throws Exception {
        Long chatId = 901L;
        TelegramParcelInfoDTO first = new TelegramParcelInfoDTO("TRACK-1", "Store Alpha", GlobalStatus.DELIVERED);
        TelegramParcelInfoDTO second = new TelegramParcelInfoDTO("TRACK-2", "Store Beta", GlobalStatus.DELIVERED);
        TelegramParcelInfoDTO third = new TelegramParcelInfoDTO("TRACK-3", "Store Alpha", GlobalStatus.DELIVERED);

        TelegramParcelsOverviewDTO overview = new TelegramParcelsOverviewDTO(
                List.of(first, second, third),
                List.of(),
                List.of());
        when(telegramService.getParcelsOverview(chatId)).thenReturn(Optional.of(overview));

        Update callbackUpdate = mockCallbackUpdate(chatId, "parcels:delivered");

        bot.consume(callbackUpdate);

        ArgumentCaptor<SendMessage> captor = ArgumentCaptor.forClass(SendMessage.class);
        verify(telegramClient, atLeastOnce()).execute(captor.capture());
        String text = captor.getValue().getText();

        assertEquals(ParseMode.MARKDOWN, captor.getValue().getParseMode(),
                "Список посылок должен отправляться в Markdown, чтобы заголовки магазинов были жирными");
        assertTrue(text.startsWith("📬 Полученные посылки"),
                "Сообщение должно начинаться с заголовка выбранной категории");
        assertTrue(text.contains("**Store Alpha**\n• TRACK-1\n• TRACK-3"),
                "Посылки одного магазина должны выводиться под общим заголовком и включать только треки");
        assertTrue(text.contains("**Store Beta**\n• TRACK-2"),
                "Для каждого магазина ожидается собственный блок с трек-номерами");
    }

    /**
     * Убеждается, что спецсимволы Markdown экранируются перед отправкой списка посылок.
     */
    @Test
    void shouldEscapeMarkdownWhenRenderingParcels() throws Exception {
        Long chatId = 903L;
        TelegramParcelInfoDTO special = new TelegramParcelInfoDTO(
                "TRACK_[1]",
                "Store_[Beta](Promo)",
                GlobalStatus.DELIVERED
        );

        TelegramParcelsOverviewDTO overview = new TelegramParcelsOverviewDTO(
                List.of(special),
                List.of(),
                List.of()
        );
        when(telegramService.getParcelsOverview(chatId)).thenReturn(Optional.of(overview));

        Update callbackUpdate = mockCallbackUpdate(chatId, "parcels:delivered");

        bot.consume(callbackUpdate);

        ArgumentCaptor<SendMessage> captor = ArgumentCaptor.forClass(SendMessage.class);
        verify(telegramClient, atLeastOnce()).execute(captor.capture());
        SendMessage message = captor.getValue();

        assertEquals(ParseMode.MARKDOWN, message.getParseMode(),
                "Ответ по посылкам должен использовать Markdown для форматирования");
        String text = message.getText();
        assertTrue(text.contains("**Store\\_\\[Beta\\]\\(Promo\\)**"),
                "Название магазина с особыми символами должно экранироваться");
        assertTrue(text.contains("• TRACK\\_\\[1\\]"),
                "Трек-номер с символами Markdown должен экранироваться");
    }

    /**
     * Проверяет, что в разделе «Ожидают забора» проблемные посылки получают предупреждение.
     */
    @Test
    void shouldWarnAboutParcelsNotPickedUpInAwaitingSection() throws Exception {
        Long chatId = 902L;
        TelegramParcelInfoDTO critical = new TelegramParcelInfoDTO(
                "TRACK-ALERT",
                "Store Gamma",
                GlobalStatus.CUSTOMER_NOT_PICKING_UP
        );
        TelegramParcelInfoDTO regular = new TelegramParcelInfoDTO(
                "TRACK-OK",
                "Store Gamma",
                GlobalStatus.WAITING_FOR_CUSTOMER
        );

        TelegramParcelsOverviewDTO overview = new TelegramParcelsOverviewDTO(
                List.of(),
                List.of(critical, regular),
                List.of()
        );
        when(telegramService.getParcelsOverview(chatId)).thenReturn(Optional.of(overview));

        Update callbackUpdate = mockCallbackUpdate(chatId, "parcels:awaiting");

        bot.consume(callbackUpdate);

        ArgumentCaptor<SendMessage> captor = ArgumentCaptor.forClass(SendMessage.class);
        verify(telegramClient, atLeastOnce()).execute(captor.capture());
        String text = captor.getValue().getText();

        assertTrue(text.contains("TRACK-ALERT — ⚠️ скоро уедет в магазин"),
                "Посылка с проблемным статусом должна сопровождаться предупреждением");
        assertTrue(text.contains("• TRACK-OK"),
                "Обычные посылки должны оставаться без дополнительных подпесей");
    }

    /**
     * Проверяет, что после сброса состояния баннер объявления вновь отображается пользователю.
     */
    @Test
    void shouldRenderAnnouncementAgainAfterReset() throws Exception {
        Long chatId = 781L;
        Customer customer = new Customer();
        customer.setTelegramChatId(chatId);
        customer.setTelegramConfirmed(true);
        customer.setNotificationsEnabled(true);
        when(telegramService.findByChatId(chatId)).thenReturn(Optional.of(customer));

        AdminNotification notification = new AdminNotification();
        notification.setId(46L);
        notification.setTitle("Повторный баннер");
        notification.setBodyLines(List.of("Первое сообщение", "Второе сообщение"));
        ZonedDateTime initialUpdatedAt = ZonedDateTime.now().minusMinutes(30);
        notification.setUpdatedAt(initialUpdatedAt);
        when(adminNotificationService.findActiveNotification()).thenReturn(Optional.of(notification));

        bot.consume(mockTextUpdate(chatId, "/start"));
        assertTrue(wasAnnouncementRendered(notification.getTitle()),
                "Первый запуск должен отрисовать баннер объявления");

        clearInvocations(telegramClient);
        chatSessionRepository.markAnnouncementSeen(chatId);

        bot.consume(mockTextUpdate(chatId, "/start"));
        assertFalse(wasAnnouncementRendered(notification.getTitle()),
                "После подтверждения баннер не должен отображаться повторно без сброса");

        clearInvocations(telegramClient);
        ChatSession session = chatSessionRepository.find(chatId)
                .orElseThrow(() -> new AssertionError("Сессия должна существовать для повторного показа"));
        ZonedDateTime resetTimestamp = initialUpdatedAt.plusMinutes(10);
        session.setAnnouncementSeen(false);
        session.setAnnouncementUpdatedAt(resetTimestamp);
        chatSessionRepository.save(session);
        notification.setUpdatedAt(resetTimestamp);

        bot.consume(mockTextUpdate(chatId, "/start"));
        assertTrue(wasAnnouncementRendered(notification.getTitle()),
                "После сброса баннер должен быть показан заново");

        ChatSession refreshed = chatSessionRepository.find(chatId)
                .orElseThrow(() -> new AssertionError("Состояние сессии должно обновиться"));
        assertEquals(resetTimestamp, refreshed.getAnnouncementUpdatedAt(),
                "После повторного показа должна обновиться отметка времени объявления");
        assertFalse(refreshed.isAnnouncementSeen(),
                "До подтверждения повторного показа объявление должно считаться непросмотренным");
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
        verifyNoMoreInteractions(telegramService);
    }

    /**
     * Гарантирует, что новый подтверждённый пользователь пропускает текущее объявление, но получает следующее активированное.
     */
    @Test
    void shouldSkipCurrentAnnouncementAfterContactButRenderNextActivation() throws Exception {
        Long chatId = 889L;

        Customer customer = new Customer();
        customer.setTelegramChatId(chatId);
        customer.setTelegramConfirmed(false);
        customer.setNotificationsEnabled(true);
        customer.setFullName("Иван Иванов");
        customer.setNameSource(NameSource.USER_CONFIRMED);

        when(telegramService.linkTelegramToCustomer(anyString(), eq(chatId))).thenAnswer(invocation -> {
            customer.setTelegramChatId(chatId);
            return customer;
        });
        when(telegramService.confirmTelegram(customer)).thenAnswer(invocation -> {
            customer.setTelegramConfirmed(true);
            return customer;
        });
        doNothing().when(telegramService).notifyActualStatuses(customer);
        when(telegramService.findByChatId(chatId)).thenReturn(
                Optional.empty(),
                Optional.of(customer),
                Optional.of(customer),
                Optional.of(customer));

        AdminNotification initialNotification = new AdminNotification();
        initialNotification.setId(66L);
        initialNotification.setTitle("Текущее объявление");
        initialNotification.setBodyLines(List.of("Старое сообщение"));
        ZonedDateTime initialUpdatedAt = ZonedDateTime.now().minusMinutes(30);
        initialNotification.setUpdatedAt(initialUpdatedAt);

        AdminNotification nextNotification = new AdminNotification();
        nextNotification.setId(67L);
        nextNotification.setTitle("Новое объявление");
        nextNotification.setBodyLines(List.of("Обновлённая информация"));
        ZonedDateTime nextUpdatedAt = initialUpdatedAt.plusMinutes(10);
        nextNotification.setUpdatedAt(nextUpdatedAt);

        AtomicReference<AdminNotification> activeNotification = new AtomicReference<>(initialNotification);
        when(adminNotificationService.findActiveNotification())
                .thenAnswer(invocation -> Optional.ofNullable(activeNotification.get()));

        Update contactUpdate = mockContactUpdate(chatId, chatId, chatId, "+375291234567");
        bot.consume(contactUpdate);

        ChatSession sessionAfterContact = chatSessionRepository.find(chatId)
                .orElseThrow(() -> new AssertionError("После контакта должна сохраняться сессия"));
        assertEquals(initialNotification.getId(), sessionAfterContact.getCurrentNotificationId(),
                "Активное объявление должно фиксироваться сразу после привязки");
        assertTrue(sessionAfterContact.isAnnouncementSeen(),
                "Новый пользователь должен считаться ознакомившимся с текущим объявлением");
        assertEquals(initialUpdatedAt, sessionAfterContact.getAnnouncementUpdatedAt(),
                "В сессии должно сохраняться время актуального объявления");
        assertNull(sessionAfterContact.getAnnouncementAnchorMessageId(),
                "Баннер не должен отправляться новому пользователю до смены объявления");

        clearInvocations(telegramClient);

        activeNotification.set(nextNotification);

        bot.consume(mockTextUpdate(chatId, "/start"));

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
                "После активации нового объявления пользователь должен увидеть баннер");

        ChatSession sessionAfterReset = chatSessionRepository.find(chatId)
                .orElseThrow(() -> new AssertionError("После смены объявления должна существовать сессия"));
        assertEquals(nextNotification.getId(), sessionAfterReset.getCurrentNotificationId(),
                "Сессия должна ссылаться на новое активное объявление");
        assertFalse(sessionAfterReset.isAnnouncementSeen(),
                "Новое объявление не должно считаться просмотренным до подтверждения");
        assertEquals(nextUpdatedAt, sessionAfterReset.getAnnouncementUpdatedAt(),
                "После активации новое время обновления должно сохраняться в сессии");
        assertNotNull(sessionAfterReset.getAnnouncementAnchorMessageId(),
                "После показа нового объявления должен сохраняться идентификатор сообщения баннера");
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
        customer.setTelegramConfirmed(true);
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
        customer.setTelegramConfirmed(true);
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
     * Проверяет, что клиент Telegram получил команду на отображение баннера с указанным заголовком.
     *
     * @param title заголовок, по которому определяется баннер
     * @return {@code true}, если среди отправленных обновлений найден нужный баннер
     */
    private boolean wasAnnouncementRendered(String title) {
        return mockingDetails(telegramClient).getInvocations().stream()
                .filter(invocation -> "execute".equals(invocation.getMethod().getName()))
                .map(invocation -> invocation.getArgument(0))
                .filter(EditMessageText.class::isInstance)
                .map(EditMessageText.class::cast)
                .map(EditMessageText::getText)
                .filter(Objects::nonNull)
                .anyMatch(text -> text.contains(title));
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
     * Создаёт мок callback-обновления для проверки обработки категорий посылок.
     *
     * @param chatId       идентификатор чата Telegram
     * @param callbackData данные callback-запроса
     * @return объект {@link Update} с настроенным callback
     */
    private Update mockCallbackUpdate(Long chatId, String callbackData) {
        Update update = mock(Update.class);
        CallbackQuery callbackQuery = mock(CallbackQuery.class);
        Message message = mock(Message.class);

        when(update.hasCallbackQuery()).thenReturn(true);
        when(update.getCallbackQuery()).thenReturn(callbackQuery);
        when(callbackQuery.getId()).thenReturn("cb-" + chatId);
        when(callbackQuery.getData()).thenReturn(callbackData);
        when(callbackQuery.getMessage()).thenReturn(message);

        when(message.getChatId()).thenReturn(chatId);
        when(message.getMessageId()).thenReturn(1);

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
