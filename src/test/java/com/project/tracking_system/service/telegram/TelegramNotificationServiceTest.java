package com.project.tracking_system.service.telegram;

import com.project.tracking_system.entity.*;
import com.project.tracking_system.service.customer.CustomerService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import static org.mockito.Mockito.*;

/**
 * Проверка генерации сообщений с учётом пользовательских шаблонов.
 */
@ExtendWith(MockitoExtension.class)
class TelegramNotificationServiceTest {

    @Mock
    private TelegramClient telegramClient;
    @Mock
    private CustomerService customerService;

    @InjectMocks
    private TelegramNotificationService service;

    @Test
    void sendStatusUpdate_CustomTemplateUsed() throws Exception {
        TrackParcel parcel = new TrackParcel();
        parcel.setNumber("123");
        Store store = new Store();
        store.setName("Shop");
        StoreTelegramSettings settings = new StoreTelegramSettings();
        settings.setStore(store);
        StoreTelegramTemplate tpl = new StoreTelegramTemplate();
        tpl.setStatus(BuyerStatus.WAITING);
        tpl.setTemplate("Test {track} {store}");
        tpl.setSettings(settings);
        settings.setTemplates(java.util.List.of(tpl));
        store.setTelegramSettings(settings);
        parcel.setStore(store);
        Customer c = new Customer();
        c.setTelegramChatId(10L);
        parcel.setCustomer(c);

        when(customerService.isNotifiable(any(), any())).thenReturn(true);

        service.sendStatusUpdate(parcel, GlobalStatus.WAITING_FOR_CUSTOMER);

        verify(telegramClient).execute(argThat(m -> ((SendMessage)m).getText().equals("Test 123 Shop")));
    }

    @Test
    void sendStatusUpdate_DefaultTemplateUsed() throws Exception {
        TrackParcel parcel = new TrackParcel();
        parcel.setNumber("123");
        Store store = new Store();
        store.setName("Shop");
        StoreTelegramSettings settings = new StoreTelegramSettings();
        settings.setStore(store);
        // шаблоны не задаём, должны использоваться значения по умолчанию
        store.setTelegramSettings(settings);
        parcel.setStore(store);
        Customer c = new Customer();
        c.setTelegramChatId(10L);
        parcel.setCustomer(c);

        when(customerService.isNotifiable(any(), any())).thenReturn(true);

        service.sendStatusUpdate(parcel, GlobalStatus.WAITING_FOR_CUSTOMER);

        String expected = BuyerStatus.WAITING.formatMessage("123", "Shop");
        verify(telegramClient).execute(argThat(m -> ((SendMessage)m).getText().equals(expected)));
    }
}
