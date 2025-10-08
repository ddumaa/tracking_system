package com.project.tracking_system.service.telegram;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.project.tracking_system.dto.ActionRequiredReturnRequestDto;
import com.project.tracking_system.dto.ReturnRequestUpdateResponse;
import com.project.tracking_system.dto.TelegramParcelInfoDTO;
import com.project.tracking_system.dto.TelegramParcelsOverviewDTO;
import com.project.tracking_system.entity.AdminNotification;
import com.project.tracking_system.entity.BuyerBotScreen;
import com.project.tracking_system.entity.BuyerChatState;
import com.project.tracking_system.entity.Customer;
import com.project.tracking_system.entity.OrderReturnRequestStatus;
import com.project.tracking_system.entity.NameSource;
import com.project.tracking_system.entity.GlobalStatus;
import com.project.tracking_system.entity.OrderReturnRequest;
import com.project.tracking_system.entity.OrderReturnRequestActionRequest;
import com.project.tracking_system.service.admin.AdminNotificationService;
import com.project.tracking_system.service.customer.CustomerTelegramService;
import com.project.tracking_system.utils.PhoneUtils;
import com.project.tracking_system.service.telegram.ChatSession;
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
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageReplyMarkup;
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
import org.springframework.security.access.AccessDeniedException;

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
    private static final String BACK_BUTTON_TEXT = "⬅️ Назад";
    private static final String NAVIGATE_BACK_CALLBACK = "nav:back";

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
        when(telegramService.getActiveReturnRequests(anyLong())).thenReturn(List.of());
        when(telegramService.getParcelsOverview(anyLong())).thenReturn(Optional.empty());
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

        assertEquals(ParseMode.MARKDOWNV2, bannerEdit.getParseMode(),
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
        TelegramParcelInfoDTO first = new TelegramParcelInfoDTO(1L, "TRACK-1", "Store Alpha", GlobalStatus.DELIVERED, false);
        TelegramParcelInfoDTO second = new TelegramParcelInfoDTO(2L, "TRACK-2", "Store Beta", GlobalStatus.DELIVERED, false);
        TelegramParcelInfoDTO third = new TelegramParcelInfoDTO(3L, "TRACK-3", "Store Alpha", GlobalStatus.DELIVERED, false);

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

        assertEquals(ParseMode.MARKDOWNV2, captor.getValue().getParseMode(),
                "Список посылок должен отправляться в Markdown, чтобы заголовки магазинов были жирными");
        assertTrue(text.startsWith("📬 Полученные посылки"),
                "Сообщение должно начинаться с заголовка выбранной категории");
        assertTrue(text.contains("*Store Alpha*\n• TRACK\\-1\n• TRACK\\-3"),
                "Посылки одного магазина должны выводиться под общим заголовком и включать только треки");
        assertTrue(text.contains("*Store Beta*\n• TRACK\\-2"),
                "Для каждого магазина ожидается собственный блок с трек-номерами");
    }

    /**
     * Убеждается, что спецсимволы Markdown экранируются перед отправкой списка посылок.
     */
    @Test
    void shouldEscapeMarkdownWhenRenderingParcels() throws Exception {
        Long chatId = 903L;
        TelegramParcelInfoDTO special = new TelegramParcelInfoDTO(
                10L,
                "TRACK_[1]",
                "Store_[Beta](Promo)",
                GlobalStatus.DELIVERED,
                false
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

        assertEquals(ParseMode.MARKDOWNV2, message.getParseMode(),
                "Ответ по посылкам должен использовать Markdown для форматирования");
        String text = message.getText();
        assertTrue(text.contains("*Store\\_\\[Beta\\]\\(Promo\\)*"),
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

    /**
     * Проверяет, что в меню возвратов отображаются кнопки действий для доставленных посылок.
     */
    @Test
    void shouldRenderReturnAndExchangeButtonsInReturnsMenu() throws Exception {
        Long chatId = 904L;
        TelegramParcelInfoDTO delivered = new TelegramParcelInfoDTO(55L, "TRACK-55", "Store Zeta", GlobalStatus.DELIVERED, false);
        TelegramParcelsOverviewDTO overview = new TelegramParcelsOverviewDTO(List.of(delivered), List.of(), List.of());
        when(telegramService.getParcelsOverview(chatId)).thenReturn(Optional.of(overview));

        Update callbackUpdate = mockCallbackUpdate(chatId, "returns:create");

        bot.consume(callbackUpdate);

        ArgumentCaptor<SendMessage> captor = ArgumentCaptor.forClass(SendMessage.class);
        verify(telegramClient, atLeastOnce()).execute(captor.capture());
        SendMessage message = captor.getValue();

        InlineKeyboardMarkup markup = (InlineKeyboardMarkup) message.getReplyMarkup();
        assertNotNull(markup, "Для меню возвратов требуется клавиатура действий");
        List<List<InlineKeyboardButton>> keyboard = markup.getKeyboard();
        assertFalse(keyboard.isEmpty(), "Клавиатура должна содержать строки");
        List<InlineKeyboardButton> firstRow = keyboard.get(0);
        assertEquals(2, firstRow.size(), "В первой строке ожидаются две кнопки действий");
        assertEquals("Вернуть", firstRow.get(0).getText());
        assertEquals("Обменять", firstRow.get(1).getText());
        assertEquals("parcel:return:55", firstRow.get(0).getCallbackData());
        assertEquals("parcel:exchange:55", firstRow.get(1).getCallbackData());
        List<InlineKeyboardButton> lastRow = keyboard.get(keyboard.size() - 1);
        assertTrue(lastRow.stream().anyMatch(button -> BACK_BUTTON_TEXT.equals(button.getText())),
                "В конце должна присутствовать кнопка навигации назад");
    }

    /**
     * Гарантирует, что главное меню возвратов содержит необходимые пункты.
     */
    @Test
    void shouldShowReturnsMenuWithAvailableOptions() throws Exception {
        Long chatId = 906L;

        Update callbackUpdate = mockCallbackUpdate(chatId, "menu:returns");

        bot.consume(callbackUpdate);

        ArgumentCaptor<SendMessage> captor = ArgumentCaptor.forClass(SendMessage.class);
        verify(telegramClient, atLeastOnce()).execute(captor.capture());
        SendMessage message = captor.getValue();

        String text = message.getText();
        assertTrue(text.contains("Возвраты и обмены"), "Текст меню должен содержать заголовок раздела");

        InlineKeyboardMarkup markup = (InlineKeyboardMarkup) message.getReplyMarkup();
        List<List<InlineKeyboardButton>> keyboard = markup.getKeyboard();
        assertEquals(3, keyboard.size(), "Ожидается две опции и строка навигации");
        assertEquals("📂 Текущие заявки", keyboard.get(0).get(0).getText());
        assertEquals("🆕 Создать заявку", keyboard.get(1).get(0).getText());
        assertEquals(BACK_BUTTON_TEXT, keyboard.get(2).get(0).getText());
    }

    /**
     * Проверяет, что при отсутствии подходящих посылок бот переиспользует якорное сообщение и показывает навигацию.
     */
    @Test
    void shouldReuseAnchorAndShowNavigationWhenNoReturnableParcels() throws Exception {
        Long chatId = 907L;
        Customer customer = new Customer();
        customer.setTelegramChatId(chatId);
        when(telegramService.findByChatId(chatId)).thenReturn(Optional.of(customer));
        TelegramParcelsOverviewDTO overview = new TelegramParcelsOverviewDTO(List.of(), List.of(), List.of());
        when(telegramService.getParcelsOverview(chatId)).thenReturn(Optional.of(overview));

        Update createCallback = mockCallbackUpdate(chatId, "returns:create", 77);
        bot.consume(createCallback);

        Update typeCallback = mockCallbackUpdate(chatId, "returns:create:type:return", 77);
        bot.consume(typeCallback);

        ArgumentCaptor<EditMessageText> editCaptor = ArgumentCaptor.forClass(EditMessageText.class);
        verify(telegramClient, atLeast(2)).execute(editCaptor.capture());
        verify(telegramClient, never()).execute(isA(SendMessage.class));

        EditMessageText lastEdit = editCaptor.getAllValues().get(editCaptor.getAllValues().size() - 1);
        assertEquals(chatId.toString(), lastEdit.getChatId());
        assertTrue(lastEdit.getText().contains("Подходящих посылок"),
                "Сообщение должно предупреждать об отсутствии доступных посылок");

        InlineKeyboardMarkup markup = lastEdit.getReplyMarkup();
        assertNotNull(markup, "Ожидается наличие инлайн-клавиатуры с навигацией");
        List<List<InlineKeyboardButton>> keyboard = markup.getKeyboard();
        assertEquals(1, keyboard.size(), "При отсутствии посылок ожидается только строка навигации");
        List<InlineKeyboardButton> navigationRow = keyboard.get(0);
        assertEquals(2, navigationRow.size(), "Строка навигации должна содержать две кнопки");
        InlineKeyboardButton backButton = navigationRow.get(0);
        InlineKeyboardButton menuButton = navigationRow.get(1);
        assertEquals(BACK_BUTTON_TEXT, backButton.getText());
        assertEquals(MENU_BUTTON_TEXT, menuButton.getText());
        assertEquals("nav:back", backButton.getCallbackData());
        assertEquals("menu:back", menuButton.getCallbackData());
    }

    /**
     * Убеждаемся, что при открытии активных заявок без привязанного покупателя бот повторно запрашивает номер телефона.
     */
    @Test
    void shouldRequestPhoneWhenActiveReturnsOpenedWithoutCustomer() throws Exception {
        Long chatId = 909L;

        Update callbackUpdate = mockCallbackUpdate(chatId, "returns:active");

        bot.consume(callbackUpdate);

        verify(telegramClient, atLeastOnce()).execute(any(AnswerCallbackQuery.class));
        verify(telegramService, never()).getActiveReturnRequests(anyLong());

        ArgumentCaptor<SendMessage> captor = ArgumentCaptor.forClass(SendMessage.class);
        verify(telegramClient, atLeastOnce()).execute(captor.capture());

        List<SendMessage> messages = captor.getAllValues();
        assertTrue(messages.stream()
                        .map(SendMessage::getText)
                        .filter(Objects::nonNull)
                        .anyMatch(text -> text.contains("Привяжите номер телефона")),
                "Пользователь должен увидеть подсказку о необходимости привязки номера");
        assertTrue(messages.stream()
                        .map(SendMessage::getReplyMarkup)
                        .filter(Objects::nonNull)
                        .anyMatch(markup -> markup instanceof ReplyKeyboardMarkup),
                "Бот обязан повторно запросить номер телефона через клавиатуру контакта");
    }

    /**
     * Проверяет, что после выбора заявки клавиатура содержит только действия и кнопку возврата к списку.
     */
    @Test
    void shouldShowOnlyActionsAfterActiveRequestSelected() throws Exception {
        Long chatId = 9871L;
        Customer customer = new Customer();
        customer.setTelegramChatId(chatId);
        when(telegramService.findByChatId(chatId)).thenReturn(Optional.of(customer));

        ActionRequiredReturnRequestDto requestDto = new ActionRequiredReturnRequestDto(
                1L,
                2L,
                "TRK-001",
                "Store",
                "Получена",
                OrderReturnRequestStatus.REGISTERED,
                OrderReturnRequestStatus.REGISTERED.getDisplayName(),
                "10.10.2024",
                "09.10.2024",
                "Причина",
                "Комментарий",
                "REV-001",
                false,
                true,
                true,
                false,
                false,
                false,
                null,
                false,
                null,
                false
        );

        when(telegramService.getReturnRequestsRequiringAction(chatId))
                .thenReturn(List.of(requestDto))
                .thenReturn(List.of(requestDto));

        bot.consume(mockCallbackUpdate(chatId, "returns:active"));

        clearInvocations(telegramClient);

        bot.consume(mockCallbackUpdate(chatId, "returns:active:select:1:2"));

        ArgumentCaptor<EditMessageText> editCaptor = ArgumentCaptor.forClass(EditMessageText.class);
        verify(telegramClient).execute(editCaptor.capture());

        EditMessageText editMessage = editCaptor.getValue();
        InlineKeyboardMarkup markup = editMessage.getReplyMarkup();
        assertNotNull(markup, "После выбора заявки должна отображаться клавиатура с действиями");
        List<List<InlineKeyboardButton>> keyboard = markup.getKeyboard();
        assertFalse(keyboard.isEmpty(), "Список строк клавиатуры не должен быть пустым");

        String messageText = editMessage.getText();
        assertTrue(messageText.contains("Текущая заявка на возврат"),
                "Заголовок выбранной заявки должен указывать на оформление возврата");

        InlineKeyboardButton firstAction = keyboard.get(0).get(0);
        assertEquals("📮 Указать трек", firstAction.getText(),
                "Первая строка после выбора должна начинаться с действий по заявке");

        boolean hasSelectionButtons = keyboard.stream()
                .filter(Objects::nonNull)
                .flatMap(List::stream)
                .filter(Objects::nonNull)
                .anyMatch(button -> {
                    String callback = button.getCallbackData();
                    return callback != null && callback.startsWith("returns:active:select:");
                });
        assertFalse(hasSelectionButtons,
                "После выбора заявки список заявок не должен отображаться в клавиатуре");

        boolean hasForbiddenCallback = keyboard.stream()
                .filter(Objects::nonNull)
                .flatMap(List::stream)
                .filter(Objects::nonNull)
                .map(InlineKeyboardButton::getCallbackData)
                .filter(Objects::nonNull)
                .anyMatch("returns:active:list"::equals);
        assertFalse(hasForbiddenCallback,
                "После выбора заявки не должно оставаться кнопок со старым callback возврата к списку");

        List<InlineKeyboardButton> navigationRow = keyboard.get(keyboard.size() - 1);
        InlineKeyboardButton backButton = navigationRow.stream()
                .filter(button -> BACK_BUTTON_TEXT.equals(button.getText()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Навигационная строка обязана содержать кнопку «Назад»"));
        assertEquals(NAVIGATE_BACK_CALLBACK, backButton.getCallbackData(),
                "Кнопка навигации «Назад» должна использовать стандартный callback");
        assertTrue(navigationRow.stream().anyMatch(button -> MENU_BUTTON_TEXT.equals(button.getText())),
                "Навигационная строка обязана содержать кнопку перехода в меню");

        boolean hasTrackAction = keyboard.stream()
                .filter(Objects::nonNull)
                .flatMap(List::stream)
                .filter(Objects::nonNull)
                .anyMatch(button -> "📮 Указать трек".equals(button.getText()));
        boolean hasCommentAction = keyboard.stream()
                .filter(Objects::nonNull)
                .flatMap(List::stream)
                .filter(Objects::nonNull)
                .anyMatch(button -> "💬 Комментарий".equals(button.getText()));
        boolean hasCancelAction = keyboard.stream()
                .filter(Objects::nonNull)
                .flatMap(List::stream)
                .filter(Objects::nonNull)
                .map(InlineKeyboardButton::getText)
                .filter(Objects::nonNull)
                .anyMatch(text -> text.startsWith("🚫 Отменить возврат"));

        assertTrue(hasTrackAction, "Клавиатура должна содержать действие обновления трека");
        assertTrue(hasCommentAction, "Клавиатура должна содержать действие обновления комментария");
        assertTrue(hasCancelAction, "Клавиатура должна содержать действие отмены возврата");

        Integer anchorMessageId = editMessage.getMessageId();
        assertNotNull(anchorMessageId,
                "Обновление сообщения после выбора заявки должно указывать идентификатор сообщения");

        clearInvocations(telegramClient);

        bot.consume(mockCallbackUpdate(chatId, NAVIGATE_BACK_CALLBACK, anchorMessageId));

        ArgumentCaptor<EditMessageText> backCaptor = ArgumentCaptor.forClass(EditMessageText.class);
        verify(telegramClient).execute(backCaptor.capture());

        EditMessageText backMessage = backCaptor.getValue();
        assertTrue(backMessage.getText().contains("Выберите заявку"),
                "После нажатия «Назад» бот обязан показать список заявок");

        InlineKeyboardMarkup backMarkup = backMessage.getReplyMarkup();
        assertNotNull(backMarkup, "Список заявок должен сопровождаться инлайн-клавиатурой");
        boolean hasSelectionButtonsAfterBack = backMarkup.getKeyboard().stream()
                .filter(Objects::nonNull)
                .flatMap(List::stream)
                .filter(Objects::nonNull)
                .anyMatch(button -> {
                    String callback = button.getCallbackData();
                    return callback != null && callback.startsWith("returns:active:select:");
                });
        assertTrue(hasSelectionButtonsAfterBack,
                "Клавиатура после возврата должна снова содержать список заявок");
    }

    /**
     * Проверяет, что после возврата в меню бот сбрасывает выбор заявки
     * и повторно показывает список без сохранённого контекста.
     */
    @Test
    void shouldResetActiveRequestSelectionAfterReturningToMenu() throws Exception {
        Long chatId = 9872L;
        Customer customer = new Customer();
        customer.setTelegramChatId(chatId);
        when(telegramService.findByChatId(chatId)).thenReturn(Optional.of(customer));

        ActionRequiredReturnRequestDto requestDto = new ActionRequiredReturnRequestDto(
                11L,
                22L,
                "TRK-RESET",
                "Store A",
                "Получена",
                OrderReturnRequestStatus.REGISTERED,
                OrderReturnRequestStatus.REGISTERED.getDisplayName(),
                "12.12.2024",
                "11.12.2024",
                "Причина",
                "Комментарий",
                "REV-RESET",
                false,
                true,
                true,
                false,
                false,
                false,
                null,
                false,
                null,
                false
        );

        when(telegramService.getReturnRequestsRequiringAction(chatId))
                .thenReturn(List.of(requestDto))
                .thenReturn(List.of(requestDto))
                .thenReturn(List.of(requestDto));

        bot.consume(mockCallbackUpdate(chatId, "returns:active"));
        bot.consume(mockCallbackUpdate(chatId, "returns:active:select:11:22"));

        ChatSession sessionAfterSelection = chatSessionRepository.find(chatId)
                .orElseThrow(() -> new AssertionError("После выбора заявки должна существовать сессия"));
        assertEquals(requestDto.requestId(), sessionAfterSelection.getActiveReturnRequestId(),
                "Выбранная заявка обязана сохраняться в сессии до выхода");

        bot.consume(mockTextUpdate(chatId, MENU_BUTTON_TEXT));

        ChatSession sessionAfterMenu = chatSessionRepository.find(chatId)
                .orElseThrow(() -> new AssertionError("Состояние должно сохраняться после возврата в меню"));
        assertNull(sessionAfterMenu.getActiveReturnRequestId(),
                "После возврата в меню активная заявка должна сбрасываться");
        assertNull(sessionAfterMenu.getReturnRequestEditMode(),
                "Возврат в меню обязан очищать ожидаемый режим редактирования");

        clearInvocations(telegramClient);

        bot.consume(mockCallbackUpdate(chatId, "returns:active"));

        ArgumentCaptor<EditMessageText> editCaptor = ArgumentCaptor.forClass(EditMessageText.class);
        verify(telegramClient, atLeastOnce()).execute(editCaptor.capture());

        assertFalse(editCaptor.getAllValues().isEmpty(),
                "Повторное открытие раздела должно обновлять якорное сообщение");
        EditMessageText lastEdit = editCaptor.getAllValues()
                .get(editCaptor.getAllValues().size() - 1);
        String text = lastEdit.getText();
        assertNotNull(text, "Повторное открытие должно сопровождаться текстом");
        assertTrue(text.contains("Выберите заявку"),
                "После сброса контекста бот обязан снова предложить выбрать заявку");
        assertFalse(text.contains("Текущая заявка"),
                "Повторное открытие не должно показывать подробности предыдущего выбора");

        InlineKeyboardMarkup markup = lastEdit.getReplyMarkup();
        assertNotNull(markup, "Экран списка должен сопровождаться клавиатурой");
        boolean hasSelectionButtons = markup.getKeyboard().stream()
                .filter(Objects::nonNull)
                .flatMap(List::stream)
                .filter(Objects::nonNull)
                .anyMatch(button -> {
                    String callback = button.getCallbackData();
                    return callback != null && callback.startsWith("returns:active:select:");
                });
        assertTrue(hasSelectionButtons,
                "После возврата в меню клавиатура должна снова содержать кнопки выбора заявок");
    }

    /**
     * Проверяет, что при выборе заявки с одобренным обменом в тексте отображается корректный заголовок.
     */
    @Test
    void shouldShowExchangeHeaderAfterActiveExchangeSelected() throws Exception {
        Long chatId = 6789L;
        Customer customer = new Customer();
        customer.setTelegramChatId(chatId);
        when(telegramService.findByChatId(chatId)).thenReturn(Optional.of(customer));

        ActionRequiredReturnRequestDto exchangeRequest = new ActionRequiredReturnRequestDto(
                5L,
                8L,
                "EX-TRK",
                "Store",
                "Доставлена",
                OrderReturnRequestStatus.EXCHANGE_APPROVED,
                OrderReturnRequestStatus.EXCHANGE_APPROVED.getDisplayName(),
                "11.11.2024",
                "10.11.2024",
                "Обмен",
                "Комментарий",
                "REV-EX",
                true,
                false,
                true,
                false,
                false,
                false,
                null,
                false,
                null,
                false
        );

        when(telegramService.getReturnRequestsRequiringAction(chatId))
                .thenReturn(List.of(exchangeRequest))
                .thenReturn(List.of(exchangeRequest));

        bot.consume(mockCallbackUpdate(chatId, "returns:active"));
        clearInvocations(telegramClient);

        bot.consume(mockCallbackUpdate(chatId, "returns:active:select:5:8"));

        ArgumentCaptor<EditMessageText> editCaptor = ArgumentCaptor.forClass(EditMessageText.class);
        verify(telegramClient).execute(editCaptor.capture());

        EditMessageText editMessage = editCaptor.getValue();
        assertTrue(editMessage.getText().contains("Текущая заявка на обмен"),
                "Заголовок выбранной заявки должен отображать оформление обмена");
    }

    @Test
    void shouldHideCancelExchangeWhenTrackAlreadyProvided() throws Exception {
        Long chatId = 6790L;
        Customer customer = new Customer();
        customer.setTelegramChatId(chatId);
        when(telegramService.findByChatId(chatId)).thenReturn(Optional.of(customer));

        String warning = "Отмена обмена недоступна: магазин уже указал трек обменной посылки.";
        ActionRequiredReturnRequestDto exchangeRequest = new ActionRequiredReturnRequestDto(
                6L,
                9L,
                "EX-TRK-2",
                "Store",
                "Доставлена",
                OrderReturnRequestStatus.EXCHANGE_APPROVED,
                OrderReturnRequestStatus.EXCHANGE_APPROVED.getDisplayName(),
                "12.11.2024",
                "11.11.2024",
                "Обмен",
                "Комментарий",
                "REV-EX",
                true,
                false,
                true,
                false,
                false,
                false,
                warning,
                false,
                null,
                false
        );

        when(telegramService.getReturnRequestsRequiringAction(chatId))
                .thenReturn(List.of(exchangeRequest))
                .thenReturn(List.of(exchangeRequest));

        bot.consume(mockCallbackUpdate(chatId, "returns:active"));
        clearInvocations(telegramClient);

        bot.consume(mockCallbackUpdate(chatId, "returns:active:select:6:9"));

        ArgumentCaptor<EditMessageText> editCaptor = ArgumentCaptor.forClass(EditMessageText.class);
        verify(telegramClient).execute(editCaptor.capture());

        EditMessageText editMessage = editCaptor.getValue();
        String messageText = editMessage.getText();
        assertTrue(messageText.contains("⚠️"), "Сообщение должно содержать предупреждение об отсутствии отмены");
        assertTrue(messageText.contains("Отмена обмена недоступна"),
                "Текст должен включать причину блокировки отмены");

        InlineKeyboardMarkup markup = editMessage.getReplyMarkup();
        assertNotNull(markup, "После выбора заявки клавиатура должна отображаться");
        boolean hasCancelButton = markup.getKeyboard().stream()
                .filter(Objects::nonNull)
                .flatMap(List::stream)
                .filter(Objects::nonNull)
                .map(InlineKeyboardButton::getText)
                .anyMatch("🚫 Отменить обмен"::equals);
        assertFalse(hasCancelButton, "Кнопка отмены обмена должна скрываться, если магазин указал трек");
    }

    @Test
    void shouldShowMerchantRequestButtonsWhenExchangeShipmentDispatched() throws Exception {
        Long chatId = 6800L;
        Customer customer = new Customer();
        customer.setTelegramChatId(chatId);
        when(telegramService.findByChatId(chatId)).thenReturn(Optional.of(customer));

        ActionRequiredReturnRequestDto exchangeRequest = new ActionRequiredReturnRequestDto(
                7L,
                10L,
                "EX-READY",
                "Store",
                "В пути",
                OrderReturnRequestStatus.EXCHANGE_APPROVED,
                OrderReturnRequestStatus.EXCHANGE_APPROVED.getDisplayName(),
                "12.11.2024",
                "11.11.2024",
                "Обмен",
                "Комментарий",
                "REV-EX",
                true,
                false,
                true,
                false,
                false,
                true,
                null,
                false,
                null,
                false
        );

        when(telegramService.getReturnRequestsRequiringAction(chatId))
                .thenAnswer(invocation -> List.of(exchangeRequest));

        bot.consume(mockCallbackUpdate(chatId, "returns:active"));
        clearInvocations(telegramClient);

        bot.consume(mockCallbackUpdate(chatId, "returns:active:select:7:10"));

        ArgumentCaptor<EditMessageText> editCaptor = ArgumentCaptor.forClass(EditMessageText.class);
        verify(telegramClient).execute(editCaptor.capture());

        InlineKeyboardMarkup markup = editCaptor.getValue().getReplyMarkup();
        assertNotNull(markup, "После выбора заявки клавиатура должна отображаться");
        List<String> buttonLabels = markup.getKeyboard().stream()
                .filter(Objects::nonNull)
                .flatMap(List::stream)
                .filter(Objects::nonNull)
                .map(InlineKeyboardButton::getText)
                .toList();

        assertTrue(buttonLabels.contains("📝 Запросить отмену обмена"),
                "При отправке обменной посылки должна отображаться кнопка запроса отмены");
        assertTrue(buttonLabels.contains("📝 Запросить возврат вместо обмена"),
                "Пользователь должен видеть кнопку запроса перевода обмена в возврат");
    }

    @Test
    void shouldRequestConfirmationBeforeCancellingReturn() throws Exception {
        Long chatId = 7001L;
        Customer customer = new Customer();
        customer.setTelegramChatId(chatId);
        when(telegramService.findByChatId(chatId)).thenReturn(Optional.of(customer));

        ActionRequiredReturnRequestDto request = new ActionRequiredReturnRequestDto(
                100L,
                200L,
                "TRK-500",
                "Store",
                "Получена",
                OrderReturnRequestStatus.REGISTERED,
                OrderReturnRequestStatus.REGISTERED.getDisplayName(),
                "01.03.2025",
                "28.02.2025",
                "Причина",
                "Комментарий",
                "REV-CNF",
                false,
                true,
                true,
                false,
                false,
                false,
                null,
                false,
                null,
                false
        );

        when(telegramService.getReturnRequestsRequiringAction(chatId))
                .thenAnswer(invocation -> List.of(request));

        bot.consume(mockCallbackUpdate(chatId, "returns:active"));
        clearInvocations(telegramClient);

        bot.consume(mockCallbackUpdate(chatId, "returns:active:select:100:200"));
        clearInvocations(telegramClient);

        bot.consume(mockCallbackUpdate(chatId, "returns:active:cancel:100:200"));

        ArgumentCaptor<EditMessageText> editCaptor = ArgumentCaptor.forClass(EditMessageText.class);
        verify(telegramClient).execute(editCaptor.capture());
        EditMessageText edit = editCaptor.getValue();
        assertNotNull(edit, "После выбора отмены бот должен отрисовать подтверждение");

        String text = edit.getText() != null ? edit.getText().replace("\\", "") : "";
        assertTrue(text.contains("REV-CNF"), "Вопрос подтверждения обязан содержать обратный трек");
        assertTrue(text.contains("Подтвердите отмену возврата"),
                "Пользователь должен увидеть запрос на подтверждение отмены");

        InlineKeyboardMarkup markup = edit.getReplyMarkup();
        assertNotNull(markup, "Подтверждение должно сопровождаться клавиатурой");
        List<List<InlineKeyboardButton>> keyboard = markup.getKeyboard();
        assertFalse(keyboard.isEmpty(), "Клавиатура подтверждения не должна быть пустой");
        List<InlineKeyboardButton> confirmationRow = keyboard.get(0);
        assertEquals("✅ Да", confirmationRow.get(0).getText(),
                "Первая кнопка подтверждения должна позволять согласиться");
        assertEquals("↩️ Нет", confirmationRow.get(1).getText(),
                "Вторая кнопка подтверждения должна отменять действие");

        assertEquals(BuyerChatState.AWAITING_ACTIVE_ACTION_CONFIRMATION, chatSessionRepository.getState(chatId),
                "После запроса подтверждения бот обязан ожидать ответа на действие");
    }

    @Test
    void shouldCreateMerchantCancellationRequestWhenExchangeAlreadyDispatched() throws Exception {
        Long chatId = 7005L;
        Customer customer = new Customer();
        customer.setTelegramChatId(chatId);
        customer.setId(200L);
        when(telegramService.findByChatId(chatId)).thenReturn(Optional.of(customer));

        ActionRequiredReturnRequestDto dispatchedExchange = new ActionRequiredReturnRequestDto(
                300L,
                400L,
                "TRK-EX",
                "Store",
                "В пути",
                OrderReturnRequestStatus.EXCHANGE_APPROVED,
                OrderReturnRequestStatus.EXCHANGE_APPROVED.getDisplayName(),
                "02.03.2025",
                "01.03.2025",
                "Обмен",
                "Комментарий",
                "REV-EX",
                true,
                false,
                true,
                false,
                false,
                true,
                null,
                false,
                null,
                false
        );

        when(telegramService.getReturnRequestsRequiringAction(chatId))
                .thenAnswer(invocation -> List.of(dispatchedExchange));

        bot.consume(mockCallbackUpdate(chatId, "returns:active"));
        clearInvocations(telegramClient);

        bot.consume(mockCallbackUpdate(chatId, "returns:active:select:300:400"));
        clearInvocations(telegramClient);

        bot.consume(mockCallbackUpdate(chatId, "returns:active:cancel_exchange:300:400"));
        clearInvocations(telegramClient);

        OrderReturnRequestActionRequest actionRequest = new OrderReturnRequestActionRequest();
        when(telegramService.requestExchangeCancellationFromTelegram(chatId, 400L, 300L)).thenReturn(actionRequest);

        bot.consume(mockCallbackUpdate(chatId, "returns:active:confirm:cancel_exchange:yes:300:400"));

        verify(telegramService).requestExchangeCancellationFromTelegram(chatId, 400L, 300L);
        verify(telegramService, never()).cancelExchangeFromTelegram(anyLong(), anyLong(), anyLong());

        ArgumentCaptor<EditMessageText> editCaptor = ArgumentCaptor.forClass(EditMessageText.class);
        verify(telegramClient).execute(editCaptor.capture());
        String finalMessage = editCaptor.getValue().getText().replace("\\", "");
        assertTrue(finalMessage.contains("Мы передали запрос магазину на отмену обмена"),
                "Финальное сообщение должно информировать пользователя о сформированном запросе");
    }

    @Test
    void shouldCancelReturnAfterConfirmation() throws Exception {
        Long chatId = 7002L;
        Customer customer = new Customer();
        customer.setTelegramChatId(chatId);
        when(telegramService.findByChatId(chatId)).thenReturn(Optional.of(customer));

        ActionRequiredReturnRequestDto request = new ActionRequiredReturnRequestDto(
                101L,
                201L,
                "TRK-501",
                "Store",
                "Получена",
                OrderReturnRequestStatus.REGISTERED,
                OrderReturnRequestStatus.REGISTERED.getDisplayName(),
                "02.03.2025",
                "28.02.2025",
                "Причина",
                "Комментарий",
                "REV-CF2",
                false,
                true,
                true,
                false,
                false,
                false,
                null,
                false,
                null,
                false
        );

        when(telegramService.getReturnRequestsRequiringAction(chatId))
                .thenAnswer(invocation -> List.of(request));

        bot.consume(mockCallbackUpdate(chatId, "returns:active"));
        bot.consume(mockCallbackUpdate(chatId, "returns:active:select:101:201"));
        clearInvocations(telegramClient);

        bot.consume(mockCallbackUpdate(chatId, "returns:active:cancel:101:201"));
        clearInvocations(telegramClient);

        bot.consume(mockCallbackUpdate(chatId, "returns:active:confirm:cancel:yes:101:201"));

        verify(telegramService).closeReturnRequestFromTelegram(chatId, 201L, 101L);

        ArgumentCaptor<EditMessageText> editCaptor = ArgumentCaptor.forClass(EditMessageText.class);
        verify(telegramClient).execute(editCaptor.capture());
        String message = editCaptor.getValue().getText();
        assertTrue(message.contains("Заявка на возврат отменена"),
                "После подтверждения бот обязан сообщить об успешной отмене");

        assertEquals(BuyerChatState.IDLE, chatSessionRepository.getState(chatId),
                "После завершения действия бот должен вернуться в состояние ожидания");
    }

    @Test
    void shouldRestoreActionsWhenCancellationDeclined() throws Exception {
        Long chatId = 7003L;
        Customer customer = new Customer();
        customer.setTelegramChatId(chatId);
        when(telegramService.findByChatId(chatId)).thenReturn(Optional.of(customer));

        ActionRequiredReturnRequestDto request = new ActionRequiredReturnRequestDto(
                102L,
                202L,
                "TRK-502",
                "Store",
                "Получена",
                OrderReturnRequestStatus.REGISTERED,
                OrderReturnRequestStatus.REGISTERED.getDisplayName(),
                "03.03.2025",
                "28.02.2025",
                "Причина",
                "Комментарий",
                null,
                false,
                true,
                true,
                false,
                false,
                false,
                null,
                false,
                null,
                false
        );

        when(telegramService.getReturnRequestsRequiringAction(chatId))
                .thenAnswer(invocation -> List.of(request));

        bot.consume(mockCallbackUpdate(chatId, "returns:active"));
        bot.consume(mockCallbackUpdate(chatId, "returns:active:select:102:202"));
        clearInvocations(telegramClient);

        bot.consume(mockCallbackUpdate(chatId, "returns:active:cancel:102:202"));
        clearInvocations(telegramClient);

        bot.consume(mockCallbackUpdate(chatId, "returns:active:confirm:cancel:no:102:202"));

        verify(telegramService, never()).closeReturnRequestFromTelegram(anyLong(), anyLong(), anyLong());

        ArgumentCaptor<EditMessageText> editCaptor = ArgumentCaptor.forClass(EditMessageText.class);
        verify(telegramClient).execute(editCaptor.capture());
        String text = editCaptor.getValue().getText();
        assertTrue(text.contains("Доступные действия"),
                "После отказа от отмены бот обязан вернуть подсказку по действиям");

        assertEquals(BuyerChatState.AWAITING_ACTIVE_REQUEST_SELECTION, chatSessionRepository.getState(chatId),
                "Отказ от действия должен вернуть пользователя к выбору действий");
    }

    @Test
    void shouldHideExchangeActionsWhenReplacementDispatched() throws Exception {
        Long chatId = 7004L;
        Customer customer = new Customer();
        customer.setTelegramChatId(chatId);
        when(telegramService.findByChatId(chatId)).thenReturn(Optional.of(customer));

        ActionRequiredReturnRequestDto exchange = new ActionRequiredReturnRequestDto(
                103L,
                203L,
                "TRK-503",
                "Store",
                "Доставлена",
                OrderReturnRequestStatus.EXCHANGE_APPROVED,
                OrderReturnRequestStatus.EXCHANGE_APPROVED.getDisplayName(),
                "04.03.2025",
                "01.03.2025",
                "Обмен",
                "Комментарий",
                null,
                true,
                false,
                true,
                false,
                false,
                true,
                null,
                false,
                null,
                false
        );

        when(telegramService.getReturnRequestsRequiringAction(chatId))
                .thenAnswer(invocation -> List.of(exchange));

        bot.consume(mockCallbackUpdate(chatId, "returns:active"));
        clearInvocations(telegramClient);

        bot.consume(mockCallbackUpdate(chatId, "returns:active:select:103:203"));

        ArgumentCaptor<EditMessageText> editCaptor = ArgumentCaptor.forClass(EditMessageText.class);
        verify(telegramClient).execute(editCaptor.capture());
        InlineKeyboardMarkup markup = editCaptor.getValue().getReplyMarkup();
        assertNotNull(markup, "Клавиатура действий должна отображаться");

        boolean hasCancelExchange = markup.getKeyboard().stream()
                .filter(Objects::nonNull)
                .flatMap(List::stream)
                .map(InlineKeyboardButton::getText)
                .filter(Objects::nonNull)
                .anyMatch("🚫 Отменить обмен"::equals);
        boolean hasConvert = markup.getKeyboard().stream()
                .filter(Objects::nonNull)
                .flatMap(List::stream)
                .map(InlineKeyboardButton::getText)
                .filter(Objects::nonNull)
                .anyMatch("↩️ Перевести в возврат"::equals);

        assertFalse(hasCancelExchange, "После отправки замены кнопка отмены обмена должна скрываться");
        assertFalse(hasConvert, "После отправки замены кнопка перевода в возврат должна скрываться");
    }

    /**
     * Проверяет, что при выборе действия обновления трека бот запрашивает ввод и сохраняет контекст заявки.
     */
    @Test
    void shouldPromptTrackUpdateWhenActionSelected() throws Exception {
        Long chatId = 1111L;
        Customer customer = new Customer();
        customer.setTelegramChatId(chatId);
        when(telegramService.findByChatId(chatId)).thenReturn(Optional.of(customer));

        ActionRequiredReturnRequestDto requestDto = new ActionRequiredReturnRequestDto(
                1L,
                2L,
                "TRK",
                "Store",
                "Получена",
                OrderReturnRequestStatus.REGISTERED,
                OrderReturnRequestStatus.REGISTERED.getDisplayName(),
                "10.10",
                "09.10",
                "Причина",
                "Комментарий",
                "REV",
                false,
                true,
                true,
                false,
                false,
                false,
                null,
                false,
                null,
                false
        );
        when(telegramService.getReturnRequestsRequiringAction(chatId))
                .thenReturn(List.of(requestDto))
                .thenReturn(List.of(requestDto))
                .thenReturn(List.of(requestDto));

        bot.consume(mockCallbackUpdate(chatId, "returns:active"));
        bot.consume(mockCallbackUpdate(chatId, "returns:active:select:1:2"));
        clearInvocations(telegramClient);

        bot.consume(mockCallbackUpdate(chatId, "returns:active:track:1:2"));

        ArgumentCaptor<SendMessage> captor = ArgumentCaptor.forClass(SendMessage.class);
        verify(telegramClient).execute(captor.capture());
        String prompt = captor.getValue().getText();
        assertTrue(prompt.contains("Отправьте трек"), "Пользователь должен увидеть подсказку по вводу трека");
        assertEquals(BuyerChatState.AWAITING_TRACK_UPDATE, chatSessionRepository.getState(chatId));
        ChatSession stored = chatSessionRepository.find(chatId).orElseThrow();
        assertEquals(ReturnRequestEditMode.TRACK, stored.getReturnRequestEditMode());
    }

    /**
     * Проверяет, что текстовое сообщение обновляет обратный трек и возвращает пользователя к списку заявок.
     */
    @Test
    void shouldUpdateTrackWhenMessageReceived() throws Exception {
        Long chatId = 1212L;
        Customer customer = new Customer();
        customer.setTelegramChatId(chatId);
        when(telegramService.findByChatId(chatId)).thenReturn(Optional.of(customer));
        ActionRequiredReturnRequestDto requestDto = new ActionRequiredReturnRequestDto(
                3L,
                2L,
                "P-1",
                "Store",
                "Доставлена",
                OrderReturnRequestStatus.REGISTERED,
                OrderReturnRequestStatus.REGISTERED.getDisplayName(),
                "01.01.2025",
                "01.01.2025",
                "Причина",
                null,
                null,
                false,
                true,
                true,
                false,
                false,
                false,
                null,
                false,
                null,
                false
        );
        when(telegramService.getReturnRequestsRequiringAction(chatId))
                .thenReturn(List.of(requestDto))
                .thenReturn(List.of());

        ReturnRequestUpdateResponse response = new ReturnRequestUpdateResponse(
                3L,
                "REV-1",
                "Комментарий",
                OrderReturnRequestStatus.REGISTERED
        );
        when(telegramService.updateReturnRequestDetailsFromTelegram(chatId, 2L, 3L, "TRACK", null))
                .thenReturn(response);

        ChatSession session = new ChatSession(chatId, BuyerChatState.AWAITING_TRACK_UPDATE, 500, BuyerBotScreen.RETURNS_ACTIVE_REQUESTS);
        session.setActiveReturnRequestContext(3L, 2L, ReturnRequestEditMode.TRACK);
        session.updateNavigationForScreen(BuyerBotScreen.RETURNS_ACTIVE_REQUESTS, false);
        chatSessionRepository.save(session);

        clearInvocations(telegramClient);
        bot.consume(mockTextUpdate(chatId, "TRACK"));

        verify(telegramService).updateReturnRequestDetailsFromTelegram(chatId, 2L, 3L, "TRACK", null);
        ArgumentCaptor<EditMessageText> editCaptor = ArgumentCaptor.forClass(EditMessageText.class);
        verify(telegramClient).execute(editCaptor.capture());
        verify(telegramClient, never()).execute(isA(SendMessage.class));

        EditMessageText editMessage = editCaptor.getValue();
        assertTrue(editMessage.getText().contains("Трек-номер сохранён"),
                "Пользователь должен увидеть подтверждение сохранения трека");

        InlineKeyboardMarkup markup = editMessage.getReplyMarkup();
        assertNotNull(markup, "Для результата операции ожидается инлайн-клавиатура");
        List<List<InlineKeyboardButton>> keyboard = markup.getKeyboard();
        assertEquals(1, keyboard.size(), "Клавиатура результата должна содержать одну навигационную строку");
        List<InlineKeyboardButton> navRow = keyboard.get(0);
        InlineKeyboardButton backButton = navRow.stream()
                .filter(button -> BACK_BUTTON_TEXT.equals(button.getText()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Результат операции обязан содержать кнопку «Назад»"));
        assertEquals(NAVIGATE_BACK_CALLBACK, backButton.getCallbackData(),
                "Кнопка возврата должна использовать стандартный callback навигации");
        boolean legacyCallbackPresent = navRow.stream()
                .map(InlineKeyboardButton::getCallbackData)
                .filter(Objects::nonNull)
                .anyMatch("returns:active:list"::equals);
        assertFalse(legacyCallbackPresent, "Клавиатура результата не должна содержать устаревший callback возврата к списку");

        assertEquals(BuyerChatState.IDLE, chatSessionRepository.getState(chatId));
        ChatSession stored = chatSessionRepository.find(chatId).orElseThrow();
        assertNull(stored.getActiveReturnRequestId(), "Контекст редактируемой заявки должен очищаться");
    }

    /**
     * Проверяет, что при ошибке доступа бот показывает кнопку возврата к списку заявок.
     */
    @Test
    void shouldShowReturnButtonWhenTrackUpdateDenied() throws Exception {
        Long chatId = 1313L;
        Customer customer = new Customer();
        customer.setTelegramChatId(chatId);
        when(telegramService.findByChatId(chatId)).thenReturn(Optional.of(customer));

        ActionRequiredReturnRequestDto requestDto = new ActionRequiredReturnRequestDto(
                7L,
                9L,
                "TRACK-ERR",
                "Store",
                "Доставлена",
                OrderReturnRequestStatus.REGISTERED,
                OrderReturnRequestStatus.REGISTERED.getDisplayName(),
                "02.02.2025",
                "01.02.2025",
                "Причина",
                "Комментарий",
                "REV-ERR",
                false,
                true,
                true,
                false,
                false,
                false,
                null
        );
        when(telegramService.getReturnRequestsRequiringAction(chatId))
                .thenReturn(List.of(requestDto));
        when(telegramService.updateReturnRequestDetailsFromTelegram(chatId, 9L, 7L, "TRACK", "Комментарий"))
                .thenThrow(new AccessDeniedException("denied"));

        ChatSession session = new ChatSession(chatId, BuyerChatState.AWAITING_TRACK_UPDATE, 600, BuyerBotScreen.RETURNS_ACTIVE_REQUESTS);
        session.setActiveReturnRequestContext(7L, 9L, ReturnRequestEditMode.TRACK);
        session.updateNavigationForScreen(BuyerBotScreen.RETURNS_ACTIVE_REQUESTS, false);
        chatSessionRepository.save(session);

        clearInvocations(telegramClient);
        bot.consume(mockTextUpdate(chatId, "TRACK"));

        verify(telegramService).updateReturnRequestDetailsFromTelegram(chatId, 9L, 7L, "TRACK", "Комментарий");

        ArgumentCaptor<EditMessageText> editCaptor = ArgumentCaptor.forClass(EditMessageText.class);
        verify(telegramClient).execute(editCaptor.capture());
        verify(telegramClient, never()).execute(isA(SendMessage.class));

        EditMessageText editMessage = editCaptor.getValue();
        assertTrue(editMessage.getText().contains("Не удалось подтвердить владельца посылки"),
                "Пользователь должен получить подсказку о невозможности обновления");

        InlineKeyboardMarkup markup = editMessage.getReplyMarkup();
        assertNotNull(markup, "Для ошибки также требуется инлайн-клавиатура");
        List<InlineKeyboardButton> navRow = markup.getKeyboard().get(0);
        InlineKeyboardButton backButton = navRow.stream()
                .filter(button -> BACK_BUTTON_TEXT.equals(button.getText()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("При ошибке должна отображаться кнопка «Назад»"));
        assertEquals(NAVIGATE_BACK_CALLBACK, backButton.getCallbackData(),
                "Кнопка возврата после ошибки обязана использовать стандартный callback");
        boolean containsLegacyCallback = navRow.stream()
                .map(InlineKeyboardButton::getCallbackData)
                .filter(Objects::nonNull)
                .anyMatch("returns:active:list"::equals);
        assertFalse(containsLegacyCallback, "Клавиатура ошибки не должна содержать устаревший callback возврата к списку");

        assertEquals(BuyerChatState.IDLE, chatSessionRepository.getState(chatId));
        ChatSession stored = chatSessionRepository.find(chatId).orElseThrow();
        assertNull(stored.getActiveReturnRequestId(), "Контекст должен очищаться после ошибки");
    }

    /**
     * Убеждаемся, что активная заявка отображается в тексте и при этом клавиатура раздела лишена действий.
     */
    @Test
    void shouldIndicateActiveRequestForDeliveredParcel() throws Exception {
        Long chatId = 905L;
        TelegramParcelInfoDTO delivered = new TelegramParcelInfoDTO(77L, "TRACK-77", "Store Eta", GlobalStatus.DELIVERED, true);
        TelegramParcelsOverviewDTO overview = new TelegramParcelsOverviewDTO(List.of(delivered), List.of(), List.of());
        when(telegramService.getParcelsOverview(chatId)).thenReturn(Optional.of(overview));

        Update callbackUpdate = mockCallbackUpdate(chatId, "parcels:delivered");

        bot.consume(callbackUpdate);

        ArgumentCaptor<SendMessage> captor = ArgumentCaptor.forClass(SendMessage.class);
        verify(telegramClient, atLeastOnce()).execute(captor.capture());
        SendMessage message = captor.getValue();
        String text = message.getText();
        assertTrue(text.contains("TRACK\\-77 — заявка в обработке"),
                "В тексте необходимо отобразить признак активной заявки");

        InlineKeyboardMarkup markup = (InlineKeyboardMarkup) message.getReplyMarkup();
        List<List<InlineKeyboardButton>> keyboard = markup.getKeyboard();
        assertEquals(1, keyboard.size(), "Для списка полученных должна остаться только навигация");
        InlineKeyboardButton backButton = keyboard.get(0).get(0);
        assertEquals(BACK_BUTTON_TEXT, backButton.getText(), "На клавиатуре ожидается кнопка возврата");
    }

    /**
     * Гарантирует, что порядок кнопок в меню возвратов совпадает с порядком вывода по магазинам.
     */
    @Test
    void shouldAlignReturnMenuKeyboardWithGroupedText() throws Exception {
        Long chatId = 908L;
        TelegramParcelInfoDTO firstAlpha = new TelegramParcelInfoDTO(101L, "TRACK-101", "Store Alpha", GlobalStatus.DELIVERED, false);
        TelegramParcelInfoDTO beta = new TelegramParcelInfoDTO(202L, "TRACK-202", "Store Beta", GlobalStatus.DELIVERED, false);
        TelegramParcelInfoDTO secondAlpha = new TelegramParcelInfoDTO(303L, "TRACK-303", "Store Alpha", GlobalStatus.DELIVERED, false);
        TelegramParcelsOverviewDTO overview = new TelegramParcelsOverviewDTO(List.of(firstAlpha, beta, secondAlpha), List.of(), List.of());
        when(telegramService.getParcelsOverview(chatId)).thenReturn(Optional.of(overview));

        Update callbackUpdate = mockCallbackUpdate(chatId, "returns:create");

        bot.consume(callbackUpdate);

        ArgumentCaptor<SendMessage> captor = ArgumentCaptor.forClass(SendMessage.class);
        verify(telegramClient, atLeastOnce()).execute(captor.capture());
        SendMessage message = captor.getValue();
        String text = message.getText();

        assertNotNull(text, "Текст меню возвратов обязателен");
        assertTrue(text.contains("Создание заявки"), "Подсказка по созданию заявки должна присутствовать");

        InlineKeyboardMarkup markup = (InlineKeyboardMarkup) message.getReplyMarkup();
        assertNotNull(markup, "Для создания заявки должна быть построена клавиатура");
        List<List<InlineKeyboardButton>> keyboard = markup.getKeyboard();
        assertTrue(keyboard.size() >= 4, "Клавиатура должна содержать строки для каждой посылки и навигацию");

        List<Long> expectedOrder = List.of(101L, 303L, 202L);
        for (int i = 0; i < expectedOrder.size(); i++) {
            List<InlineKeyboardButton> row = keyboard.get(i);
            assertEquals(2, row.size(), "Каждая строка действий содержит две кнопки");
            assertEquals("parcel:return:" + expectedOrder.get(i), row.get(0).getCallbackData());
            assertEquals("parcel:exchange:" + expectedOrder.get(i), row.get(1).getCallbackData());
        }
    }

    /**
     * Проверяет, что callback возврата приводит к отправке подтверждающего сообщения.
     */
    @Test
    void shouldHandleReturnCallbackAndSendConfirmation() throws Exception {
        Long chatId = 906L;
        TelegramParcelInfoDTO delivered = new TelegramParcelInfoDTO(88L, "TRACK-88", "Store Theta", GlobalStatus.DELIVERED, false);
        TelegramParcelsOverviewDTO overview = new TelegramParcelsOverviewDTO(List.of(delivered), List.of(), List.of());
        when(telegramService.getParcelsOverview(chatId)).thenReturn(Optional.of(overview));

        Update callbackUpdate = mockCallbackUpdate(chatId, "parcel:return:88");

        bot.consume(callbackUpdate);

        verify(telegramClient, atLeastOnce()).execute(any(AnswerCallbackQuery.class));
        verify(telegramClient, atLeastOnce()).execute(argThat(method -> {
            String text = null;
            if (method instanceof SendMessage sendMessage) {
                text = sendMessage.getText();
            } else if (method instanceof EditMessageText editMessage) {
                text = editMessage.getText();
            }
            return text != null && text.contains("TRACK\\-88") && text.contains("причин");
        }));
        verify(telegramClient, atLeastOnce()).execute(argThat(method -> {
            if (!(method instanceof EditMessageReplyMarkup editMarkup)) {
                return false;
            }
            return Objects.equals(chatId.toString(), editMarkup.getChatId())
                    && Objects.equals(1, editMarkup.getMessageId())
                    && editMarkup.getReplyMarkup() == null;
        }));
    }

    /**
     * Проверяет последовательный сбор данных для возврата с сохранением промежуточного состояния.
     */
    @Test
    void shouldCollectReturnFlowStepByStep() throws Exception {
        Long chatId = 1006L;
        TelegramParcelInfoDTO delivered = new TelegramParcelInfoDTO(77L, "TRACK-77", "Store Sigma", GlobalStatus.DELIVERED, false);
        TelegramParcelsOverviewDTO overview = new TelegramParcelsOverviewDTO(List.of(delivered), List.of(), List.of());
        when(telegramService.getParcelsOverview(chatId)).thenReturn(Optional.of(overview));

        bot.consume(mockCallbackUpdate(chatId, "parcel:return:77"));

        assertEquals(BuyerChatState.AWAITING_RETURN_REASON, chatSessionRepository.getState(chatId),
                "После старта сценария бот должен ожидать причину возврата");
        ChatSession session = chatSessionRepository.find(chatId).orElseThrow();
        assertEquals(77L, session.getReturnParcelId(), "Идентификатор посылки должен сохраняться в сессии");
        assertEquals("TRACK-77", session.getReturnParcelTrackNumber(), "Трек посылки должен сохраняться");
        assertEquals(BuyerBotScreen.RETURNS_RETURN_REASON, session.getLastScreen(),
                "После отправки списка причин экран должен соответствовать шагу выбора причины");
        Integer returnReasonAnchorId = session.getAnchorMessageId();
        assertNotNull(returnReasonAnchorId, "Сообщение с причинами должно становиться текущим якорем");
        verify(telegramClient, atLeastOnce()).execute(argThat(method -> {
            String text = null;
            if (method instanceof SendMessage sendMessage) {
                text = sendMessage.getText();
            } else if (method instanceof EditMessageText editMessage) {
                text = editMessage.getText();
            }
            return text != null && text.contains("TRACK\\-77") && text.contains("причин");
        }));
        verify(telegramClient, atLeastOnce()).execute(argThat(method -> {
            if (!(method instanceof EditMessageReplyMarkup editMarkup)) {
                return false;
            }
            return Objects.equals(chatId.toString(), editMarkup.getChatId())
                    && Objects.equals(1, editMarkup.getMessageId())
                    && editMarkup.getReplyMarkup() == null;
        }));

        when(telegramService.registerReturnRequestFromTelegram(anyLong(), anyLong(), anyString(), anyString()))
                .thenReturn(new OrderReturnRequest());

        bot.consume(mockCallbackUpdate(chatId, "returns:create:reason:not_fit", returnReasonAnchorId));

        assertEquals(BuyerChatState.IDLE, chatSessionRepository.getState(chatId),
                "После выбора причины бот должен завершить сценарий");
        session = chatSessionRepository.find(chatId).orElseThrow();
        assertEquals(77L, session.getReturnParcelId(),
                "Контекст заявки должен сохраняться до подтверждения пользователем");
        assertEquals("TRACK-77", session.getReturnParcelTrackNumber(),
                "Трек посылки сохраняется для отображения в подтверждении");
        verify(telegramService).registerReturnRequestFromTelegram(eq(chatId), eq(77L), anyString(), eq("Не подошло"));

        ArgumentCaptor<SendMessage> captor = ArgumentCaptor.forClass(SendMessage.class);
        verify(telegramClient, atLeastOnce()).execute(captor.capture());
        SendMessage summary = captor.getValue();
        String text = summary.getText();
        assertTrue(text.contains("Зафиксировали запрос на возврат"),
                "Итоговое сообщение должно подтверждать регистрацию запроса");
        assertTrue(text.contains("Не подошло"),
                "В сообщении должна отображаться выбранная причина");
        assertTrue(text.contains("📂 Текущие заявки"),
                "В сообщении должно быть напоминание о разделе для добавления трека");
        assertTrue(summary.getReplyMarkup() instanceof InlineKeyboardMarkup,
                "Финальное сообщение должно содержать инлайн-клавиатуру");
        InlineKeyboardMarkup markup = (InlineKeyboardMarkup) summary.getReplyMarkup();
        List<List<InlineKeyboardButton>> rows = markup.getKeyboard();
        boolean hasDoneButton = rows.stream()
                .filter(Objects::nonNull)
                .flatMap(List::stream)
                .filter(Objects::nonNull)
                .anyMatch(button -> "Хорошо".equals(button.getText())
                        && "returns:done".equals(button.getCallbackData()));
        assertTrue(hasDoneButton, "Пользователь должен видеть кнопку возврата в меню");
        boolean hasActiveButton = rows.stream()
                .filter(Objects::nonNull)
                .flatMap(List::stream)
                .filter(Objects::nonNull)
                .anyMatch(button -> "📂 Текущие заявки".equals(button.getText())
                        && "returns:active".equals(button.getCallbackData()));
        assertTrue(hasActiveButton, "Финальная клавиатура должна содержать кнопку перехода к заявкам");

        Integer completionAnchorMessageId = session.getAnchorMessageId();
        assertNotNull(completionAnchorMessageId, "Якорное сообщение финального экрана должно сохраняться");
        Update doneCallback = mock(Update.class);
        CallbackQuery callbackQuery = mock(CallbackQuery.class);
        Message callbackMessage = mock(Message.class);
        when(doneCallback.hasCallbackQuery()).thenReturn(true);
        when(doneCallback.getCallbackQuery()).thenReturn(callbackQuery);
        when(callbackQuery.getId()).thenReturn("cb-" + chatId + "-done");
        when(callbackQuery.getData()).thenReturn("returns:done");
        when(callbackQuery.getMessage()).thenReturn(callbackMessage);
        when(callbackMessage.getChatId()).thenReturn(chatId);
        when(callbackMessage.getMessageId()).thenReturn(completionAnchorMessageId);

        bot.consume(doneCallback);

        ChatSession clearedSession = chatSessionRepository.find(chatId).orElseThrow();
        assertNull(clearedSession.getReturnParcelId(),
                "После подтверждения данные временной заявки должны очищаться");
        assertEquals(BuyerBotScreen.MENU, clearedSession.getLastScreen(),
                "После возвращения в меню должен сохраняться экран главного меню");
    }

    /**
     * Проверяет, что при повторном нажатии на устаревшее сообщение бот восстанавливает экран выбора причины возврата.
     */
    @Test
    void shouldRestoreReturnReasonPromptAfterOutdatedCallback() throws Exception {
        Long chatId = 1116L;
        TelegramParcelInfoDTO delivered = new TelegramParcelInfoDTO(55L, "TRACK-55", "Store Rho", GlobalStatus.DELIVERED, false);
        TelegramParcelsOverviewDTO overview = new TelegramParcelsOverviewDTO(List.of(delivered), List.of(), List.of());
        when(telegramService.getParcelsOverview(chatId)).thenReturn(Optional.of(overview));

        bot.consume(mockCallbackUpdate(chatId, "parcel:return:55"));

        ChatSession session = chatSessionRepository.find(chatId).orElseThrow();
        Integer originalAnchor = session.getAnchorMessageId();
        assertNotNull(originalAnchor, "Сообщение с причинами должно сохраняться как якорь");
        assertEquals(BuyerBotScreen.RETURNS_RETURN_REASON, session.getLastScreen(),
                "После старта сценария должен запоминаться экран выбора причины");

        chatSessionRepository.updateAnchor(chatId, originalAnchor + 50);

        clearInvocations(telegramClient);

        doAnswer(invocation -> {
            EditMessageText editMessage = invocation.getArgument(0);
            if (Objects.equals(editMessage.getMessageId(), originalAnchor + 50)) {
                throw new TelegramApiException("Bad Request: message to edit not found");
            }
            return null;
        }).when(telegramClient).execute(any(EditMessageText.class));

        bot.consume(mockCallbackUpdate(chatId, "returns:create:reason:defect", originalAnchor));

        verify(telegramClient, atLeastOnce()).execute(argThat(method -> {
            if (!(method instanceof EditMessageReplyMarkup editMarkup)) {
                return false;
            }
            return Objects.equals(chatId.toString(), editMarkup.getChatId())
                    && Objects.equals(originalAnchor, editMarkup.getMessageId())
                    && editMarkup.getReplyMarkup() == null;
        }));
        verify(telegramClient, atLeastOnce()).execute(argThat(method -> {
            if (!(method instanceof EditMessageReplyMarkup editMarkup)) {
                return false;
            }
            return Objects.equals(chatId.toString(), editMarkup.getChatId())
                    && Objects.equals(originalAnchor + 50, editMarkup.getMessageId())
                    && editMarkup.getReplyMarkup() == null;
        }));
        ArgumentCaptor<SendMessage> captor = ArgumentCaptor.forClass(SendMessage.class);
        verify(telegramClient).execute(captor.capture());
        SendMessage resentPrompt = captor.getValue();
        assertEquals(chatId.toString(), resentPrompt.getChatId(),
                "Сообщение восстановления должно отправляться в исходный чат");
        assertTrue(resentPrompt.getText().contains("TRACK\\-55"),
                "В восстановленном сообщении должен отображаться трек посылки");
        assertTrue(resentPrompt.getReplyMarkup() instanceof InlineKeyboardMarkup,
                "Сообщение должно содержать инлайн-клавиатуру с причинами");

        ChatSession refreshedSession = chatSessionRepository.find(chatId).orElseThrow();
        Integer refreshedAnchor = refreshedSession.getAnchorMessageId();
        assertNotNull(refreshedAnchor, "После восстановления экрана должен сохраняться новый якорь");
        assertNotEquals(originalAnchor, refreshedAnchor,
                "Новый якорь не должен совпадать со старым сообщением");
        assertNotEquals(originalAnchor + 50, refreshedAnchor,
                "Хранившийся ранее якорь должен быть заменён на актуальный");
        assertEquals(BuyerBotScreen.RETURNS_RETURN_REASON, refreshedSession.getLastScreen(),
                "Сеанс должен оставаться на шаге выбора причины");
    }

    /**
     * Проверяет, что на экране выбора причины доступны кнопки навигации и они возвращают пользователя на предыдущие шаги.
     */
    @Test
    void shouldNavigateFromReturnReasonPrompt() throws Exception {
        Long chatId = 2116L;
        TelegramParcelInfoDTO delivered = new TelegramParcelInfoDTO(77L, "TRACK-77", "Store Sigma", GlobalStatus.DELIVERED, false);
        TelegramParcelsOverviewDTO overview = new TelegramParcelsOverviewDTO(List.of(delivered), List.of(), List.of());
        when(telegramService.getParcelsOverview(chatId)).thenReturn(Optional.of(overview));

        bot.consume(mockCallbackUpdate(chatId, "parcel:return:77"));

        ArgumentCaptor<SendMessage> promptCaptor = ArgumentCaptor.forClass(SendMessage.class);
        verify(telegramClient, atLeastOnce()).execute(promptCaptor.capture());
        SendMessage reasonPrompt = promptCaptor.getAllValues().stream()
                .filter(message -> {
                    String text = message.getText();
                    return text != null && text.contains("причину ниже");
                })
                .findFirst()
                .orElseThrow(() -> new AssertionError("Не найдено сообщение с выбором причины"));

        assertTrue(reasonPrompt.getReplyMarkup() instanceof InlineKeyboardMarkup,
                "Сообщение с выбором причины должно содержать инлайн-клавиатуру");
        InlineKeyboardMarkup reasonMarkup = (InlineKeyboardMarkup) reasonPrompt.getReplyMarkup();
        List<List<InlineKeyboardButton>> reasonRows = reasonMarkup.getKeyboard();
        assertEquals(3, reasonRows.size(),
                "Клавиатура выбора причины должна содержать две строки причин и навигацию");
        List<InlineKeyboardButton> navigationRow = reasonRows.get(2);
        assertEquals(2, navigationRow.size(),
                "В навигационной строке должны быть кнопки «Назад» и «Меню»");
        InlineKeyboardButton backButton = navigationRow.get(0);
        InlineKeyboardButton menuButton = navigationRow.get(1);
        assertEquals(BACK_BUTTON_TEXT, backButton.getText(),
                "Текст кнопки возврата должен соответствовать шаблону");
        assertEquals(MENU_BUTTON_TEXT, menuButton.getText(),
                "Текст кнопки меню должен совпадать с шаблоном");
        assertEquals("nav:back", backButton.getCallbackData(),
                "Callback кнопки возврата должен указывать на навигацию назад");
        assertEquals("menu:back", menuButton.getCallbackData(),
                "Callback кнопки меню должен вести в главное меню");

        ChatSession session = chatSessionRepository.find(chatId).orElseThrow();
        Integer reasonAnchorId = session.getAnchorMessageId();
        assertNotNull(reasonAnchorId, "Сообщение выбора причины должно становиться якорным");

        clearInvocations(telegramClient);

        bot.consume(mockCallbackUpdate(chatId, "nav:back", reasonAnchorId));

        verify(telegramClient, atLeastOnce()).execute(argThat(method -> {
            if (!(method instanceof EditMessageText editMessage)) {
                return false;
            }
            String text = editMessage.getText();
            return text != null && text.contains("Выберите посылку из магазина");
        }));

        ChatSession afterBackSession = chatSessionRepository.find(chatId).orElseThrow();
        assertEquals(BuyerBotScreen.RETURNS_CREATE_REQUEST, afterBackSession.getLastScreen(),
                "После нажатия «Назад» бот должен вернуть пользователя к выбору посылки");

        Integer parcelAnchorId = afterBackSession.getAnchorMessageId();
        assertNotNull(parcelAnchorId, "Экран выбора посылки должен иметь актуальный якорь");

        bot.consume(mockCallbackUpdate(chatId, "returns:create:parcel:77", parcelAnchorId));

        ChatSession reasonAgainSession = chatSessionRepository.find(chatId).orElseThrow();
        Integer reasonAnchorAgain = reasonAgainSession.getAnchorMessageId();
        assertNotNull(reasonAnchorAgain, "Повторное открытие экрана причины должно обновлять якорь");
        assertEquals(BuyerBotScreen.RETURNS_RETURN_REASON, reasonAgainSession.getLastScreen(),
                "После повторного выбора посылки пользователь снова видит экран причины");

        clearInvocations(telegramClient);

        bot.consume(mockCallbackUpdate(chatId, "menu:back", reasonAnchorAgain));

        verify(telegramClient, atLeastOnce()).execute(argThat(method -> {
            if (!(method instanceof EditMessageText editMessage)) {
                return false;
            }
            String text = editMessage.getText();
            return text != null && text.contains("Главное меню");
        }));

        ChatSession afterMenuSession = chatSessionRepository.find(chatId).orElseThrow();
        assertEquals(BuyerBotScreen.MENU, afterMenuSession.getLastScreen(),
                "После нажатия кнопки меню должен отображаться главный экран");
        assertEquals(BuyerChatState.IDLE, chatSessionRepository.getState(chatId),
                "При возврате в меню сценарий оформления возврата должен завершаться");
    }

    /**
     * Проверяет, что callback обмена открывает экран выбора причины с нужной клавиатурой.
     */
    @Test
    void shouldHandleExchangeCallbackAndShowReasonKeyboard() throws Exception {
        Long chatId = 907L;
        TelegramParcelInfoDTO delivered = new TelegramParcelInfoDTO(99L, "TRACK-99", "Store Iota", GlobalStatus.DELIVERED, false);
        TelegramParcelsOverviewDTO overview = new TelegramParcelsOverviewDTO(List.of(delivered), List.of(), List.of());
        when(telegramService.getParcelsOverview(chatId)).thenReturn(Optional.of(overview));

        Update callbackUpdate = mockCallbackUpdate(chatId, "parcel:exchange:99");

        bot.consume(callbackUpdate);

        verify(telegramClient, atLeastOnce()).execute(any(AnswerCallbackQuery.class));

        ChatSession session = chatSessionRepository.find(chatId).orElseThrow();
        assertEquals(BuyerChatState.AWAITING_EXCHANGE_REASON, chatSessionRepository.getState(chatId),
                "После старта обмена бот должен ожидать выбор причины");
        assertEquals(99L, session.getReturnParcelId(), "В сессии должен сохраняться идентификатор посылки");
        assertEquals("TRACK-99", session.getReturnParcelTrackNumber(), "В сессии должен сохраняться трек посылки");
        assertEquals(BuyerBotScreen.RETURNS_EXCHANGE_REASON, session.getLastScreen(),
                "Последний экран должен соответствовать шагу выбора причины обмена");
        assertNotNull(session.getAnchorMessageId(), "Сообщение с причинами обмена должно становиться якорем");

        ArgumentCaptor<SendMessage> captor = ArgumentCaptor.forClass(SendMessage.class);
        verify(telegramClient, atLeastOnce()).execute(captor.capture());
        SendMessage prompt = captor.getValue();
        assertNotNull(prompt.getText(), "Текст подсказки для обмена обязателен");
        assertTrue(prompt.getText().contains("📩 Начинаем оформление обмена"),
                "Подсказка должна сообщать о начале оформления обмена");
        assertTrue(prompt.getText().contains("TRACK\\-99"),
                "В подсказке должен отображаться трек выбранной посылки");
        assertTrue(prompt.getReplyMarkup() instanceof InlineKeyboardMarkup,
                "Сообщение должно сопровождаться инлайн-клавиатурой с причинами");
        InlineKeyboardMarkup markup = (InlineKeyboardMarkup) prompt.getReplyMarkup();
        List<List<InlineKeyboardButton>> rows = markup.getKeyboard();
        assertFalse(rows.isEmpty(), "Клавиатура с причинами не должна быть пустой");
        boolean hasReasonButtons = rows.stream()
                .flatMap(List::stream)
                .filter(Objects::nonNull)
                .map(InlineKeyboardButton::getText)
                .filter(Objects::nonNull)
                .anyMatch(text -> text.contains("Не подошло") || text.contains("Брак"));
        assertTrue(hasReasonButtons, "Клавиатура обязана содержать варианты причин обмена");
    }

    /**
     * Проверяет, что выбор причины обмена приводит к регистрации заявки и показу итогового экрана.
     */
    @Test
    void shouldFinalizeExchangeAfterReasonSelection() throws Exception {
        Long chatId = 908L;
        TelegramParcelInfoDTO delivered = new TelegramParcelInfoDTO(77L, "TRACK-77", "Store Sigma", GlobalStatus.DELIVERED, false);
        TelegramParcelsOverviewDTO overview = new TelegramParcelsOverviewDTO(List.of(delivered), List.of(), List.of());
        when(telegramService.getParcelsOverview(chatId)).thenReturn(Optional.of(overview));

        bot.consume(mockCallbackUpdate(chatId, "parcel:exchange:77"));

        ChatSession session = chatSessionRepository.find(chatId).orElseThrow();
        Integer anchorId = session.getAnchorMessageId();
        assertNotNull(anchorId, "Экран выбора причины обмена должен сохранять якорное сообщение");

        OrderReturnRequest registered = mock(OrderReturnRequest.class);
        when(registered.getId()).thenReturn(555L);
        when(telegramService.registerReturnRequestFromTelegram(eq(chatId), eq(77L), anyString(), anyString()))
                .thenReturn(registered);
        when(telegramService.approveExchangeFromTelegram(chatId, 77L, 555L))
                .thenReturn(registered);

        Update reasonCallback = mockCallbackUpdate(chatId, "returns:create:reason:defect", anchorId);
        bot.consume(reasonCallback);

        ArgumentCaptor<String> reasonCaptor = ArgumentCaptor.forClass(String.class);
        verify(telegramService).registerReturnRequestFromTelegram(eq(chatId), eq(77L), anyString(), reasonCaptor.capture());
        assertEquals("Брак", reasonCaptor.getValue(),
                "В сервис заявок должна передаваться выбранная пользователем причина обмена");

        verify(telegramService).approveExchangeFromTelegram(chatId, 77L, 555L);

        ArgumentCaptor<SendMessage> messageCaptor = ArgumentCaptor.forClass(SendMessage.class);
        verify(telegramClient, atLeastOnce()).execute(messageCaptor.capture());
        SendMessage summary = messageCaptor.getValue();
        assertNotNull(summary.getText(), "Финальное сообщение обмена не должно быть пустым");
        assertTrue(summary.getText().contains("Зафиксировали запрос на обмен"),
                "Сообщение должно подтверждать регистрацию обмена");
        assertTrue(summary.getText().contains("Брак"),
                "В итоговой сводке должна отображаться выбранная причина");
        assertTrue(summary.getReplyMarkup() instanceof InlineKeyboardMarkup,
                "Финальное сообщение должно сопровождаться инлайн-клавиатурой");
        InlineKeyboardMarkup summaryMarkup = (InlineKeyboardMarkup) summary.getReplyMarkup();
        boolean hasOkButton = summaryMarkup.getKeyboard().stream()
                .flatMap(List::stream)
                .filter(Objects::nonNull)
                .anyMatch(button -> "Ок".equals(button.getText())
                        && "returns:done".equals(button.getCallbackData()));
        assertTrue(hasOkButton, "Клавиатура итогового экрана должна содержать кнопку «Ок»");

        ChatSession updatedSession = chatSessionRepository.find(chatId).orElseThrow();
        assertEquals(BuyerBotScreen.RETURNS_EXCHANGE_COMPLETION, updatedSession.getLastScreen(),
                "После регистрации обмена должен отображаться экран подтверждения");
        assertEquals(BuyerChatState.IDLE, chatSessionRepository.getState(chatId),
                "Сценарное состояние после оформления обмена должно возвращаться к ожиданию команд");
        assertEquals("Брак", updatedSession.getReturnReason(),
                "Причина обмена должна сохраняться до подтверждения пользователем");
        assertNotNull(updatedSession.getAnchorMessageId(),
                "Сообщение подтверждения обмена должно быть текущим якорем");

        verify(telegramClient, atLeastOnce()).execute(any(AnswerCallbackQuery.class));
    }

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

        assertTrue(text.contains("TRACK\\-ALERT — ⚠️ скоро уедет в магазин"),
                "Посылка с проблемным статусом должна сопровождаться предупреждением");
        assertTrue(text.contains("• TRACK\\-OK"),
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
        return mockCallbackUpdate(chatId, callbackData, 1);
    }

    /**
     * Создаёт мок callback-обновления с явным указанием исходного сообщения.
     *
     * @param chatId       идентификатор чата Telegram
     * @param callbackData данные callback-запроса
     * @param messageId    идентификатор сообщения, из которого пришёл callback
     * @return объект {@link Update} с настроенным callback
     */
    private Update mockCallbackUpdate(Long chatId, String callbackData, Integer messageId) {
        Update update = mock(Update.class);
        CallbackQuery callbackQuery = mock(CallbackQuery.class);
        Message message = mock(Message.class);

        when(update.hasCallbackQuery()).thenReturn(true);
        when(update.getCallbackQuery()).thenReturn(callbackQuery);
        when(callbackQuery.getId()).thenReturn("cb-" + chatId);
        when(callbackQuery.getData()).thenReturn(callbackData);
        when(callbackQuery.getMessage()).thenReturn(message);

        when(message.getChatId()).thenReturn(chatId);
        when(message.getMessageId()).thenReturn(messageId);

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
