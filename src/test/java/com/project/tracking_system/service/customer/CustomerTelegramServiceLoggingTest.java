package com.project.tracking_system.service.customer;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.project.tracking_system.entity.Customer;
import com.project.tracking_system.repository.CustomerNotificationLogRepository;
import com.project.tracking_system.repository.CustomerRepository;
import com.project.tracking_system.repository.TrackParcelRepository;
import com.project.tracking_system.repository.OrderReturnRequestRepository;
import com.project.tracking_system.service.telegram.FullNameValidator;
import com.project.tracking_system.service.telegram.TelegramNotificationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Тесты, контролирующие отсутствие персональных данных в логах сервиса Telegram.
 */
@ExtendWith(MockitoExtension.class)
class CustomerTelegramServiceLoggingTest {

    @Mock
    private CustomerRepository customerRepository;
    @Mock
    private CustomerService customerService;
    @Mock
    private TrackParcelRepository trackParcelRepository;
    @Mock
    private CustomerNotificationLogRepository notificationLogRepository;
    @Mock
    private TelegramNotificationService telegramNotificationService;
    @Mock
    private OrderReturnRequestRepository returnRequestRepository;

    @Spy
    private FullNameValidator fullNameValidator = new FullNameValidator();

    @InjectMocks
    private CustomerTelegramService customerTelegramService;

    private Logger logger;
    private ListAppender<ILoggingEvent> appender;

    @BeforeEach
    void setUp() {
        logger = (Logger) LoggerFactory.getLogger(CustomerTelegramService.class);
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
     * Проверяет, что при успешной привязке телефона в логи попадает только маскированный номер.
     */
    @Test
    void whenLinkSuccessful_thenInfoLogContainsMaskedPhone() {
        Customer customer = new Customer();
        customer.setId(5L);

        when(customerService.registerOrGetByPhone("375291112233")).thenReturn(customer);
        when(customerRepository.save(any(Customer.class))).thenAnswer(invocation -> invocation.getArgument(0));

        customerTelegramService.linkTelegramToCustomer("+375291112233", 42L);

        boolean maskedLogged = appender.list.stream()
                .filter(event -> event.getLevel() == Level.INFO)
                .map(ILoggingEvent::getFormattedMessage)
                .filter(message -> message.contains("Попытка привязки"))
                .anyMatch(message -> message.contains("37529111***") && !message.contains("375291112233"));

        assertTrue(maskedLogged, "Информационный лог должен содержать маскированный телефон");
    }

    /**
     * Проверяет, что при ошибке регистрации телефона в лог пишется только маска номера.
     */
    @Test
    void whenRegisterFails_thenWarnLogContainsMaskedPhone() {
        when(customerService.registerOrGetByPhone("375291112233"))
                .thenThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Некорректный номер"));

        assertThrows(ResponseStatusException.class,
                () -> customerTelegramService.linkTelegramToCustomer("+375291112233", 42L));

        boolean maskedLogged = appender.list.stream()
                .filter(event -> event.getLevel() == Level.WARN)
                .map(ILoggingEvent::getFormattedMessage)
                .anyMatch(message -> message.contains("37529111***") && !message.contains("375291112233"));

        assertTrue(maskedLogged, "Предупреждающий лог должен содержать маскированный телефон");
    }
}
