package com.project.tracking_system.service.telegram;

import com.project.tracking_system.entity.CustomerTelegramLink;
import com.project.tracking_system.service.customer.CustomerTelegramService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Contact;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Тесты для {@link BuyerTelegramBot}.
 */
@ExtendWith(MockitoExtension.class)
class BuyerTelegramBotTest {

    @Mock
    private TelegramClient telegramClient;
    @Mock
    private CustomerTelegramService telegramService;

    private BuyerTelegramBot bot;

    @BeforeEach
    void setUp() {
        bot = new BuyerTelegramBot(null, telegramClient, "token", telegramService);
    }

    @Test
    void consume_WithContact_SendsConfirmationAndKeyboard() throws TelegramApiException {
        // создаём контакт и сообщение
        Contact contact = new Contact();
        contact.setPhoneNumber("+375291234567");

        Message message = mock(Message.class);
        when(message.hasText()).thenReturn(false);
        when(message.hasContact()).thenReturn(true);
        when(message.getChatId()).thenReturn(100L);
        when(message.getContact()).thenReturn(contact);

        Update update = new Update();
        update.setMessage(message);

        CustomerTelegramLink link = new CustomerTelegramLink();
        link.setTelegramChatId(100L);
        link.setTelegramConfirmed(false);
        link.setLinkedAt(ZonedDateTime.now(ZoneOffset.UTC));

        when(telegramService.linkTelegramToCustomer(anyString(), anyLong())).thenReturn(link);
        when(telegramClient.execute(any(SendMessage.class))).thenReturn(null);

        bot.consume(update);

        // проверяем отправку двух сообщений
        ArgumentCaptor<SendMessage> captor = ArgumentCaptor.forClass(SendMessage.class);
        verify(telegramClient, times(2)).execute(captor.capture());
        List<SendMessage> messages = captor.getAllValues();
        assertEquals("✅ Номер сохранён. Спасибо!", messages.get(0).getText());
        assertNull(messages.get(0).getReplyMarkup());
        assertEquals("🔔 Настройки уведомлений", messages.get(1).getText());
        assertNotNull(messages.get(1).getReplyMarkup());

        verify(telegramService).confirmTelegram(link);
        verify(telegramService).notifyActualStatuses(link);
    }
}
