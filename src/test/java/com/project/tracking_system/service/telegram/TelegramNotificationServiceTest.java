package com.project.tracking_system.service.telegram;

import com.project.tracking_system.entity.Customer;
import com.project.tracking_system.entity.GlobalStatus;
import com.project.tracking_system.entity.Store;
import com.project.tracking_system.entity.StoreTelegramSettings;
import com.project.tracking_system.entity.TrackParcel;
import com.project.tracking_system.service.customer.CustomerService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Тесты для {@link TelegramNotificationService}, контролирующие ранние выходы.
 */
@ExtendWith(MockitoExtension.class)
class TelegramNotificationServiceTest {

    @Mock
    private TelegramClient telegramClient;

    @Mock
    private CustomerService customerService;

    @InjectMocks
    private TelegramNotificationService telegramNotificationService;

    /**
     * Проверяет, что при запрете на уведомления метод возвращает {@code false} и не отправляет сообщение.
     */
    @Test
    void sendStatusUpdate_whenCustomerNotNotifiable_returnsFalse() throws TelegramApiException {
        TrackParcel parcel = buildParcel();

        when(customerService.isNotifiable(parcel.getCustomer(), parcel.getStore())).thenReturn(false);

        boolean result = telegramNotificationService.sendStatusUpdate(parcel, GlobalStatus.DELIVERED);

        assertFalse(result, "Метод обязан вернуть false при раннем выходе");
        verify(telegramClient, never()).execute(any(SendMessage.class));
    }

    /**
     * Проверяет, что при выполнении условий сообщение отправляется и метод возвращает {@code true}.
     */
    @Test
    void sendStatusUpdate_whenConditionsMet_returnsTrue() throws TelegramApiException {
        TrackParcel parcel = buildParcel();
        Store store = parcel.getStore();
        StoreTelegramSettings settings = new StoreTelegramSettings();
        settings.setEnabled(true);
        store.setTelegramSettings(settings);

        when(customerService.isNotifiable(parcel.getCustomer(), store)).thenReturn(true);
        when(telegramClient.execute(any(SendMessage.class))).thenReturn(null);

        boolean result = telegramNotificationService.sendStatusUpdate(parcel, GlobalStatus.DELIVERED);

        assertTrue(result, "Метод обязан сообщать об успешной отправке");
        verify(telegramClient).execute(any(SendMessage.class));
    }

    /**
     * Создаёт заготовку посылки с покупателем и магазином для тестов.
     */
    private TrackParcel buildParcel() {
        TrackParcel parcel = new TrackParcel();
        parcel.setNumber("RB000000001BY");

        Customer customer = new Customer();
        customer.setId(1L);
        customer.setTelegramChatId(123456L);
        customer.setNotificationsEnabled(true);
        parcel.setCustomer(customer);

        Store store = new Store();
        store.setId(5L);
        store.setName("Store");
        parcel.setStore(store);

        return parcel;
    }
}

