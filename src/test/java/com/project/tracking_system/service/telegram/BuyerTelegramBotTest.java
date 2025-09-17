package com.project.tracking_system.service.telegram;

import com.project.tracking_system.service.customer.CustomerTelegramService;
import com.project.tracking_system.utils.PhoneUtils;
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
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboard;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Тесты для {@link BuyerTelegramBot}, проверяющие распознавание телефона из текста.
 */
@ExtendWith(MockitoExtension.class)
class BuyerTelegramBotTest {

    @Mock
    private TelegramClient telegramClient;

    @Mock
    private CustomerTelegramService telegramService;

    @Mock
    private BuyerBotScreenStateService screenStateService;

    private BuyerTelegramBot bot;
    private FullNameValidator fullNameValidator;

    /**
     * Подготавливает экземпляр бота и стаб под клиента Telegram перед каждым тестом.
     */
    @BeforeEach
    void setUp() {
        fullNameValidator = new FullNameValidator();
        bot = new BuyerTelegramBot(telegramClient, "token", telegramService, fullNameValidator, screenStateService);
        doReturn(null).when(telegramClient).execute(any(SendMessage.class));
        when(screenStateService.findState(anyLong())).thenReturn(Optional.empty());
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
        verifyNoInteractions(telegramService);
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
        verifyNoInteractions(telegramService);
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
     * Помечает чат как ожидающий контакта через отражение приватного состояния бота.
     *
     * @param chatId идентификатор чата Telegram
     */
    private void markAwaitingContact(Long chatId) throws Exception {
        Field field = BuyerTelegramBot.class.getDeclaredField("chatStates");
        field.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<Long, BuyerTelegramBot.ChatState> states = (Map<Long, BuyerTelegramBot.ChatState>) field.get(bot);
        states.put(chatId, BuyerTelegramBot.ChatState.AWAITING_CONTACT);
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
}
