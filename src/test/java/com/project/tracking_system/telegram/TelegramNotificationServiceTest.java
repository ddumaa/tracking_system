package com.project.tracking_system.telegram;

import com.project.tracking_system.entity.*;
import com.project.tracking_system.service.customer.CustomerService;
import com.project.tracking_system.service.telegram.BuyerTelegramBot;
import com.project.tracking_system.service.telegram.TelegramNotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Проверка логики отправки уведомлений с учётом настроек магазина.
 */
class TelegramNotificationServiceTest {

    private BuyerTelegramBot bot;
    private CustomerService customerService;
    private TelegramNotificationService service;

    @BeforeEach
    void setup() {
        bot = mock(BuyerTelegramBot.class);
        customerService = mock(CustomerService.class);
        service = new TelegramNotificationService(bot, customerService);
    }

    @Test
    void reminderNotSentWhenDisabled() throws Exception {
        TrackParcel parcel = buildParcel(false, null);
        when(customerService.isNotifiable(any(), any())).thenReturn(true);

        service.sendReminder(parcel);

        verify(bot, never()).execute(any(SendMessage.class));
    }

    @Test
    void customSignatureAppended() throws Exception {
        TrackParcel parcel = buildParcel(true, "--sig");
        when(customerService.isNotifiable(any(), any())).thenReturn(true);

        service.sendReminder(parcel);

        ArgumentCaptor<SendMessage> captor = ArgumentCaptor.forClass(SendMessage.class);
        verify(bot).execute(captor.capture());
        assertTrue(captor.getValue().getText().endsWith("--sig"));
    }

    private TrackParcel buildParcel(boolean enabled, String signature) {
        Store store = new Store();
        StoreTelegramSettings settings = new StoreTelegramSettings();
        settings.setEnabled(enabled);
        settings.setCustomSignature(signature);
        settings.setStore(store);
        store.setTelegramSettings(settings);

        Customer customer = new Customer();
        customer.setTelegramChatId(1L);

        TrackParcel parcel = new TrackParcel();
        parcel.setCustomer(customer);
        parcel.setStore(store);
        return parcel;
    }
}
