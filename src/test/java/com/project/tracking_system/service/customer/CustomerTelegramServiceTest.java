package com.project.tracking_system.service.customer;

import com.project.tracking_system.entity.Customer;
import com.project.tracking_system.entity.NameSource;
import com.project.tracking_system.repository.CustomerNotificationLogRepository;
import com.project.tracking_system.repository.CustomerRepository;
import com.project.tracking_system.repository.TrackParcelRepository;
import com.project.tracking_system.service.telegram.TelegramNotificationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Тесты для {@link CustomerTelegramService}, проверяющие подтверждение имени.
 */
@ExtendWith(MockitoExtension.class)
class CustomerTelegramServiceTest {

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

    @InjectMocks
    private CustomerTelegramService customerTelegramService;

    /**
     * Убеждаемся, что при подтверждении существующего ФИО источник обновляется до USER_CONFIRMED.
     */
    @Test
    void confirmName_whenFullNameExistsAndSourceNotConfirmed_updatesSourceAndTimestamp() {
        Long chatId = 42L;
        ZonedDateTime oldTimestamp = ZonedDateTime.now(ZoneOffset.UTC).minusDays(1);

        Customer customer = new Customer();
        customer.setId(1L);
        customer.setTelegramChatId(chatId);
        customer.setFullName("Иван Иванов");
        customer.setNameSource(NameSource.MERCHANT_PROVIDED);
        customer.setNameUpdatedAt(oldTimestamp);

        when(customerRepository.findByTelegramChatId(chatId)).thenReturn(Optional.of(customer));
        when(customerRepository.save(any(Customer.class))).thenAnswer(invocation -> invocation.getArgument(0));

        boolean result = customerTelegramService.confirmName(chatId);

        assertTrue(result, "Метод должен вернуть истину при успешном подтверждении");
        assertEquals(NameSource.USER_CONFIRMED, customer.getNameSource(),
                "Источник имени обязан переключиться на USER_CONFIRMED");
        assertNotNull(customer.getNameUpdatedAt(), "Дата обновления имени должна быть установлена");
        assertTrue(customer.getNameUpdatedAt().isAfter(oldTimestamp),
                "Метка времени обязана быть новее предыдущей");

        verify(customerRepository).save(customer);
        verify(customerService, never()).updateCustomerName(any(), anyString(), any());
    }
}
