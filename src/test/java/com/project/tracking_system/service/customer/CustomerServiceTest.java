package com.project.tracking_system.service.customer;

import com.project.tracking_system.entity.Customer;
import com.project.tracking_system.entity.NameSource;
import com.project.tracking_system.entity.Role;
import com.project.tracking_system.repository.CustomerRepository;
import com.project.tracking_system.repository.TrackParcelRepository;
import com.project.tracking_system.service.SubscriptionService;
import com.project.tracking_system.service.user.UserSettingsService;
import com.project.tracking_system.service.customer.CustomerNameEventService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.telegram.telegrambots.meta.generics.TelegramClient;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Тесты для {@link CustomerService}.
 */
@ExtendWith(MockitoExtension.class)
class CustomerServiceTest {

    @Mock
    private CustomerRepository customerRepository;
    @Mock
    private TrackParcelRepository trackParcelRepository;
    @Mock
    private SubscriptionService subscriptionService;
    @Mock
    private UserSettingsService userSettingsService;
    @Mock
    private CustomerNameEventService customerNameEventService;
    @Mock
    private CustomerTransactionalService transactionalService;
    @Mock
    private TelegramClient telegramClient;

    @InjectMocks
    private CustomerService service;

    private Customer savedCustomer;

    @BeforeEach
    void setUp() {
        savedCustomer = new Customer();
        savedCustomer.setId(1L);
        savedCustomer.setPhone("375291234567");
    }

    /**
     * Проверяем нормализацию номера и сохранение нового покупателя.
     */
    @Test
    void registerOrGetByPhone_NewCustomer_SavesWithNormalizedPhone() {
        when(transactionalService.findByPhone("375291234567"))
                .thenReturn(Optional.empty());
        when(transactionalService.saveCustomer(any(Customer.class)))
                .thenReturn(savedCustomer);

        Customer result = service.registerOrGetByPhone("+375 (29) 123-45-67");

        assertSame(savedCustomer, result);
        verify(transactionalService).findByPhone("375291234567");
        verify(transactionalService).saveCustomer(argThat(c -> "375291234567".equals(c.getPhone())));
    }

    /**
     * Проверяем повторный поиск после ошибки сохранения из-за дубликата.
     */
    @Test
    void registerOrGetByPhone_DuplicateRetry_ReturnsExisting() {
        when(transactionalService.findByPhone("375291234567"))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(savedCustomer));
        when(transactionalService.saveCustomer(any(Customer.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate"));

        Customer result = service.registerOrGetByPhone("375291234567");

        assertSame(savedCustomer, result);
        verify(transactionalService, times(2)).findByPhone("375291234567");
        verify(transactionalService).saveCustomer(any(Customer.class));
    }

    /**
     * Если поиск не удаётся после нескольких попыток, бросается исключение.
     */
    @Test
    void registerOrGetByPhone_RetriesExhausted_Throws() {
        when(transactionalService.findByPhone("375291234567"))
                .thenReturn(Optional.empty());
        when(transactionalService.saveCustomer(any(Customer.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate"));
        when(transactionalService.findByPhone("375291234567"))
                .thenReturn(Optional.empty());

        assertThrows(IllegalStateException.class,
                () -> service.registerOrGetByPhone("375291234567"));

        verify(transactionalService, times(4)).findByPhone("375291234567");

    }

    /**
     * Проверяем, что поиск по телефону нормализует номер и обращается к репозиторию.
     */
    @Test
    void findByPhone_NormalizesAndDelegatesToRepository() {
        when(customerRepository.findByPhone("375291234567"))
                .thenReturn(Optional.of(savedCustomer));

        Optional<Customer> result = service.findByPhone("+375 (29) 123-45-67");

        assertTrue(result.isPresent());
        assertSame(savedCustomer, result.get());
        verify(customerRepository).findByPhone("375291234567");
    }

    /**
     * Если номер пустой, репозиторий не вызывается и возвращается пустой результат.
     */
    @Test
    void findByPhone_BlankPhone_ReturnsEmpty() {
        Optional<Customer> result = service.findByPhone("   ");

        assertTrue(result.isEmpty());
        verifyNoInteractions(customerRepository);
    }

    /**
     * Некорректный номер телефона приводит к исключению с кодом 400.
     */
    @Test
    void registerOrGetByPhone_InvalidPhone_ThrowsBadRequest() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.registerOrGetByPhone("abc"));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        verifyNoInteractions(transactionalService);
    }

    /**
     * Проверяем, что при попытке установить прежнее ФИО уведомление не отправляется.
     */
    @Test
    void updateCustomerName_SameValueForConfirmedName_DoesNotNotify() {
        Customer customer = new Customer();
        customer.setId(42L);
        customer.setFullName("Иван Иванов");
        customer.setNameSource(NameSource.USER_CONFIRMED);
        customer.setTelegramChatId(123456789L);

        boolean updated = service.updateCustomerName(
                customer,
                "Иван Иванов",
                NameSource.MERCHANT_PROVIDED,
                Role.ROLE_ADMIN
        );

        assertFalse(updated);
        verifyNoInteractions(customerRepository);
        verifyNoInteractions(customerNameEventService);
        verifyNoInteractions(telegramClient);
    }
}
