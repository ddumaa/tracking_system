package com.project.tracking_system.telegram;

import com.project.tracking_system.entity.*;
import com.project.tracking_system.repository.*;
import com.project.tracking_system.service.telegram.TelegramNotificationService;
import com.project.tracking_system.service.telegram.TelegramReminderScheduler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;

import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Интеграционный тест планировщика напоминаний.
 */
@SpringBootTest
class TelegramReminderSchedulerTest {

    @Autowired
    private TrackParcelRepository trackParcelRepository;
    @Autowired
    private CustomerNotificationLogRepository logRepository;
    @Autowired
    private StoreRepository storeRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private StoreTelegramSettingsRepository settingsRepository;
    @Autowired
    private TelegramReminderScheduler scheduler;

    @MockBean
    private TelegramNotificationService notificationService;

    @Test
    void reminderScheduledAccordingToSettings() {
        User user = new User();
        user.setEmail("t@example.com");
        user.setPassword("p");
        user.setRole(Role.ROLE_USER);
        userRepository.save(user);

        Store store = new Store();
        store.setName("s");
        store.setDefault(false);
        store.setOwner(user);
        storeRepository.save(store);

        StoreTelegramSettings settings = new StoreTelegramSettings();
        settings.setStore(store);
        settings.setReminderStartAfterDays(1);
        settings.setReminderRepeatIntervalDays(1);
        settingsRepository.save(settings);

        Customer customer = new Customer();
        customer.setPhone("375291111111");
        customer.setTelegramChatId(5L);

        TrackParcel parcel = new TrackParcel();
        parcel.setNumber("TR1");
        parcel.setStatus(GlobalStatus.WAITING_FOR_CUSTOMER);
        parcel.setData(ZonedDateTime.now(ZoneOffset.UTC));
        parcel.setStore(store);
        parcel.setUser(user);
        parcel.setCustomer(customer);
        DeliveryHistory history = new DeliveryHistory();
        history.setTrackParcel(parcel);
        history.setStore(store);
        history.setPostalService(PostalServiceType.BELPOST);
        history.setArrivedDate(ZonedDateTime.now(ZoneOffset.UTC).minusDays(2));
        parcel.setDeliveryHistory(history);
        trackParcelRepository.save(parcel);

        scheduler.sendReminders();

        assertEquals(1, logRepository.count());
        verify(notificationService).sendReminder(any(TrackParcel.class));
    }
}
