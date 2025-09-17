package com.project.tracking_system.service.telegram;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.project.tracking_system.entity.Customer;
import com.project.tracking_system.entity.NameSource;
import com.project.tracking_system.service.customer.CustomerTelegramService;
import com.project.tracking_system.service.telegram.support.InMemoryChatSessionRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Contact;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Тесты безопасного логирования телеграм-бота покупателей.
 */
@ExtendWith(MockitoExtension.class)
class BuyerTelegramBotLoggingTest {

    @Mock
    private TelegramClient telegramClient;
    @Mock
    private CustomerTelegramService customerTelegramService;

    private BuyerTelegramBot buyerTelegramBot;
    private InMemoryChatSessionRepository chatSessionRepository;

    private Logger logger;
    private ListAppender<ILoggingEvent> appender;

    @BeforeEach
    void setUp() {
        chatSessionRepository = new InMemoryChatSessionRepository();
        buyerTelegramBot = new BuyerTelegramBot(telegramClient, "token", customerTelegramService,
                new FullNameValidator(), chatSessionRepository);
        when(telegramClient.execute(any(SendMessage.class))).thenReturn(null);
        logger = (Logger) LoggerFactory.getLogger(BuyerTelegramBot.class);
        appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(appender);
        appender.stop();
    }

    /**
     * Проверяет, что бот логирует только технические метаданные обновления
     * и маскирует телефон из контакта.
     */
    @Test
    void whenContactUpdateConsumed_thenPhoneMaskedInLog() {
        Update update = mock(Update.class);
        Message message = mock(Message.class);
        Contact contact = mock(Contact.class);

        when(update.hasMessage()).thenReturn(true);
        when(update.getMessage()).thenReturn(message);
        when(message.hasText()).thenReturn(false);
        when(message.getChatId()).thenReturn(123L);
        when(message.hasContact()).thenReturn(true);
        when(message.getContact()).thenReturn(contact);
        when(contact.getPhoneNumber()).thenReturn("+375291112233");

        Customer customer = new Customer();
        customer.setTelegramConfirmed(true);
        customer.setFullName("Тест");
        customer.setNameSource(NameSource.USER_CONFIRMED);
        when(customerTelegramService.linkTelegramToCustomer(anyString(), anyLong())).thenReturn(customer);

        buyerTelegramBot.consume(update);

        String logMessage = appender.list.stream()
                .map(ILoggingEvent::getFormattedMessage)
                .filter(messageText -> messageText.contains("📩 Получено обновление"))
                .findFirst()
                .orElse(null);

        assertNotNull(logMessage, "Лог обновления должен присутствовать");
        assertTrue(logMessage.contains("type=message"), "Должен фиксироваться тип апдейта");
        assertTrue(logMessage.contains("chatId=123"), "Должен фиксироваться chatId");
        assertTrue(logMessage.contains("phone=+37529111***"), "Телефон должен маскироваться");
        assertFalse(logMessage.contains("+375291112233"), "В лог не должен попадать полный телефон");
    }
}
