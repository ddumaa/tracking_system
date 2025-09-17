package com.project.tracking_system.service.telegram;

import com.project.tracking_system.entity.Customer;
import com.project.tracking_system.entity.NameSource;
import com.project.tracking_system.service.customer.CustomerTelegramService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Chat;
import org.telegram.telegrambots.meta.api.objects.Contact;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.util.Optional;

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

    /**
     * Создаёт экземпляр бота перед каждым сценарием и стабилизирует клиент Telegram.
     */
    @BeforeEach
    void setUp() throws Exception {
        fullNameValidator = new FullNameValidator();
        bot = new BuyerTelegramBot(telegramClient, "token", telegramService, fullNameValidator);
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

        assertEquals(BuyerTelegramBot.ChatState.AWAITING_CONTACT, bot.getState(chatId),
                "Состояние должно перейти в ожидание контакта");

        ArgumentCaptor<SendMessage> captor = ArgumentCaptor.forClass(SendMessage.class);
        verify(telegramClient).execute(captor.capture());
        SendMessage message = captor.getValue();

        assertTrue(message.getText().contains("номер"),
                "Пользователь должен получить приглашение поделиться номером");
        assertNotNull(message.getReplyMarkup(),
                "Должна отправляться клавиатура запроса контакта");
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

        assertEquals(BuyerTelegramBot.ChatState.AWAITING_NAME_INPUT, bot.getState(chatId),
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

        assertEquals(BuyerTelegramBot.ChatState.IDLE, bot.getState(chatId),
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
        assertEquals(BuyerTelegramBot.ChatState.AWAITING_CONTACT, bot.getState(chatId));
        clearInvocations(telegramClient);

        bot.consume(textUpdate(chatId, "/stats"));

        assertEquals(BuyerTelegramBot.ChatState.AWAITING_CONTACT, bot.getState(chatId),
                "Бот должен продолжать ожидать контакт");
        ArgumentCaptor<SendMessage> captor = ArgumentCaptor.forClass(SendMessage.class);
        verify(telegramClient).execute(captor.capture());
        assertTrue(captor.getValue().getText().contains("поделитесь контактом"),
                "Пользователь должен получить напоминание об отправке контакта");
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
        assertEquals(BuyerTelegramBot.ChatState.AWAITING_NAME_INPUT, bot.getState(chatId));
        clearInvocations(telegramClient);

        bot.consume(textUpdate(chatId, "Верно"));

        assertEquals(BuyerTelegramBot.ChatState.AWAITING_NAME_INPUT, bot.getState(chatId),
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
        assertEquals(BuyerTelegramBot.ChatState.AWAITING_NAME_INPUT, bot.getState(chatId));
        clearInvocations(telegramClient);

        when(telegramService.findByChatId(chatId)).thenReturn(Optional.of(customer));

        bot.consume(textUpdate(chatId, "/menu"));

        assertEquals(BuyerTelegramBot.ChatState.IDLE, bot.getState(chatId),
                "Команда /menu должна переводить бот в состояние IDLE");
        ArgumentCaptor<SendMessage> captor = ArgumentCaptor.forClass(SendMessage.class);
        verify(telegramClient, atLeastOnce()).execute(captor.capture());
        boolean hasMenuMessage = captor.getAllValues().stream()
                .map(SendMessage::getText)
                .filter(text -> text != null)
                .anyMatch(text -> text.contains("Главное меню"));
        assertTrue(hasMenuMessage, "Бот должен показать главное меню");
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
        assertEquals(BuyerTelegramBot.ChatState.AWAITING_NAME_INPUT, bot.getState(chatId));
        clearInvocations(telegramClient);

        bot.consume(textUpdate(chatId, "Иван123"));

        assertEquals(BuyerTelegramBot.ChatState.AWAITING_NAME_INPUT, bot.getState(chatId),
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
        assertEquals(BuyerTelegramBot.ChatState.AWAITING_NAME_INPUT, bot.getState(chatId));
        clearInvocations(telegramClient);

        bot.consume(textUpdate(chatId, "да"));

        assertEquals(BuyerTelegramBot.ChatState.IDLE, bot.getState(chatId),
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
        assertEquals(BuyerTelegramBot.ChatState.AWAITING_NAME_INPUT, bot.getState(chatId));
        clearInvocations(telegramClient);

        bot.consume(textUpdate(chatId, "ок"));

        assertEquals(BuyerTelegramBot.ChatState.AWAITING_NAME_INPUT, bot.getState(chatId),
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

        assertEquals(BuyerTelegramBot.ChatState.IDLE, bot.getState(chatId),
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
}
