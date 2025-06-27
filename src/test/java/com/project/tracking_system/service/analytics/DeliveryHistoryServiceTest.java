package com.project.tracking_system.service.analytics;

import com.project.tracking_system.dto.TrackInfoListDTO;
import com.project.tracking_system.entity.*;
import com.project.tracking_system.repository.*;
import com.project.tracking_system.service.customer.CustomerService;
import com.project.tracking_system.service.customer.CustomerStatsService;
import com.project.tracking_system.service.telegram.TelegramNotificationService;
import com.project.tracking_system.service.track.StatusTrackService;
import com.project.tracking_system.service.track.TypeDefinitionTrackPostService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import com.project.tracking_system.entity.NotificationType;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/**
 * Проверяет отправку Telegram-уведомлений при обновлении истории доставки.
 */
@ExtendWith(MockitoExtension.class)
class DeliveryHistoryServiceTest {

    @Mock
    private StoreAnalyticsRepository storeAnalyticsRepository;
    @Mock
    private DeliveryHistoryRepository deliveryHistoryRepository;
    @Mock
    private TypeDefinitionTrackPostService typeDefinitionTrackPostService;
    @Mock
    private StatusTrackService statusTrackService;
    @Mock
    private TrackParcelRepository trackParcelRepository;
    @Mock
    private PostalServiceStatisticsRepository postalServiceStatisticsRepository;
    @Mock
    private StoreDailyStatisticsRepository storeDailyStatisticsRepository;
    @Mock
    private PostalServiceDailyStatisticsRepository postalServiceDailyStatisticsRepository;
    @Mock
    private CustomerService customerService;
    @Mock
    private CustomerStatsService customerStatsService;
    @Mock
    private TelegramNotificationService telegramNotificationService;
    @Mock
    private CustomerNotificationLogRepository customerNotificationLogRepository;

    @InjectMocks
    private DeliveryHistoryService service;

    private TrackParcel parcel;
    private Customer customer;
    private Store store;
    private User user;

    @BeforeEach
    void init() {
        user = new User();
        user.setTimeZone("UTC");
        user.setId(1L);

        store = new Store();
        store.setId(2L);
        store.setOwner(user);

        customer = new Customer();
        customer.setId(3L);

        parcel = new TrackParcel();
        parcel.setId(4L);
        parcel.setNumber("TEST");
        parcel.setStore(store);
        parcel.setUser(user);
        parcel.setCustomer(customer);
    }

    @Test
    void updateDeliveryHistory_Notifiable_SendsNotification() {
        when(deliveryHistoryRepository.findByTrackParcelId(4L)).thenReturn(Optional.empty());
        when(typeDefinitionTrackPostService.detectPostalService(any())).thenReturn(PostalServiceType.BELPOST);
        when(customerService.isNotifiable(customer, store)).thenReturn(true);
        when(customerNotificationLogRepository.existsByParcelIdAndStatusAndNotificationType(4L, GlobalStatus.WAITING_FOR_CUSTOMER, NotificationType.INSTANT)).thenReturn(false);

        service.updateDeliveryHistory(parcel, GlobalStatus.REGISTERED, GlobalStatus.WAITING_FOR_CUSTOMER, new TrackInfoListDTO());

        verify(customerService).isNotifiable(customer, store);
        verify(customerNotificationLogRepository).existsByParcelIdAndStatusAndNotificationType(4L, GlobalStatus.WAITING_FOR_CUSTOMER, NotificationType.INSTANT);
        verify(telegramNotificationService).sendStatusUpdate(parcel, GlobalStatus.WAITING_FOR_CUSTOMER);
        verify(customerNotificationLogRepository).save(any());
    }

    @Test
    void updateDeliveryHistory_NotNotifiable_DoesNotSendNotification() {
        when(deliveryHistoryRepository.findByTrackParcelId(4L)).thenReturn(Optional.empty());
        when(typeDefinitionTrackPostService.detectPostalService(any())).thenReturn(PostalServiceType.BELPOST);
        when(customerService.isNotifiable(customer, store)).thenReturn(false);

        service.updateDeliveryHistory(parcel, GlobalStatus.REGISTERED, GlobalStatus.WAITING_FOR_CUSTOMER, new TrackInfoListDTO());

        verify(customerService).isNotifiable(customer, store);
        verify(customerNotificationLogRepository, never()).existsByParcelIdAndStatusAndNotificationType(anyLong(), any(), any());
        verify(telegramNotificationService, never()).sendStatusUpdate(any(), any());
        verify(customerNotificationLogRepository, never()).save(any());
    }
}
