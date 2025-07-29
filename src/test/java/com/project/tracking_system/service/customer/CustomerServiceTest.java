package com.project.tracking_system.service.customer;

import com.project.tracking_system.entity.Customer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

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
    private CustomerTransactionalService transactionalService;

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
}
