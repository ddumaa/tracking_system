package com.project.tracking_system.service.analytics;

import com.project.tracking_system.dto.TrackInfoDTO;
import com.project.tracking_system.dto.TrackInfoListDTO;
import com.project.tracking_system.entity.*;
import com.project.tracking_system.model.subscription.FeatureKey;
import com.project.tracking_system.repository.*;
import com.project.tracking_system.service.SubscriptionService;
import com.project.tracking_system.service.customer.CustomerService;
import com.project.tracking_system.service.customer.CustomerStatsService;
import com.project.tracking_system.service.track.StatusTrackService;
import com.project.tracking_system.service.track.TypeDefinitionTrackPostService;
import com.project.tracking_system.service.telegram.TelegramNotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.project.tracking_system.service.order.OrderEpisodeLifecycleService;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Тесты для {@link DeliveryHistoryService}.
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
    @Mock
    private SubscriptionService subscriptionService;
    @Mock
    private DeliveryMetricsRollbackService deliveryMetricsRollbackService;
    @Mock
    private OrderEpisodeLifecycleService orderEpisodeLifecycleService;

    @InjectMocks
    private DeliveryHistoryService deliveryHistoryService;

    @BeforeEach
    void setupEpisodes() {
        doAnswer(invocation -> {
            TrackParcel parcel = invocation.getArgument(0);
            OrderEpisode episode = parcel.getEpisode();
            if (episode == null) {
                episode = new OrderEpisode();
                parcel.setEpisode(episode);
            }
            return episode;
        }).when(orderEpisodeLifecycleService).ensureEpisode(any());

        doAnswer(invocation -> {
            TrackParcel parcel = invocation.getArgument(0);
            if (parcel.getEpisode() == null) {
                parcel.setEpisode(new OrderEpisode());
            }
            return null;
        }).when(orderEpisodeLifecycleService).syncEpisodeCustomer(any());
    }

    /**
     * Проверяет, что метод не выбрасывает исключение,
     * если история доставки для посылки отсутствует.
     */
    @Test
    void registerFinalStatus_HistoryAbsent_NoException() {
        Long parcelId = 1L;
        when(deliveryHistoryRepository.findByTrackParcelId(parcelId)).thenReturn(Optional.empty());

        assertDoesNotThrow(() -> deliveryHistoryService.registerFinalStatus(parcelId));
    }

    /**
     * Проверяет, что при регрессе статуса с финального на промежуточный выполняется откат статистики.
     */
    @Test
    void updateDeliveryHistory_FinalStatusRegressed_RollsBackStatistics() {
        TrackParcel trackParcel = new TrackParcel();
        trackParcel.setId(1L);
        trackParcel.setNumber("RB123");
        trackParcel.setIncludedInStatistics(true);

        User owner = new User();
        owner.setId(100L);
        owner.setTimeZone("UTC");

        Store store = new Store();
        store.setId(10L);
        store.setName("Test Store");
        store.setOwner(owner);
        trackParcel.setStore(store);
        trackParcel.setUser(owner);

        Customer customer = new Customer();
        customer.setId(5L);
        customer.setSentCount(3);
        customer.setPickedUpCount(2);
        trackParcel.setCustomer(customer);

        DeliveryHistory history = new DeliveryHistory();
        history.setTrackParcel(trackParcel);
        history.setStore(store);
        history.setPostalService(PostalServiceType.BELPOST);
        ZonedDateTime sendDate = ZonedDateTime.now(ZoneOffset.UTC).minusDays(5);
        ZonedDateTime arrivedDate = sendDate.plusDays(3);
        ZonedDateTime receivedDate = arrivedDate.plusDays(1);
        history.setSendDate(sendDate);
        history.setArrivedDate(arrivedDate);
        history.setReceivedDate(receivedDate);
        trackParcel.setDeliveryHistory(history);

        when(deliveryHistoryRepository.findByTrackParcelId(trackParcel.getId())).thenReturn(Optional.of(history));
        when(typeDefinitionTrackPostService.detectPostalService(anyString())).thenReturn(PostalServiceType.BELPOST);
        when(statusTrackService.setStatus(anyList())).thenReturn(GlobalStatus.WAITING_FOR_CUSTOMER);
        when(trackParcelRepository.save(trackParcel)).thenReturn(trackParcel);
        when(customerStatsService.incrementSent(any(Customer.class))).thenAnswer(invocation -> invocation.getArgument(0));
        doNothing().when(customerService).rollbackStatsOnTrackDelete(any(TrackParcel.class));
        doNothing().when(deliveryMetricsRollbackService).rollbackFinalStatusMetrics(history, trackParcel, GlobalStatus.DELIVERED);

        TrackInfoListDTO trackInfoListDTO = new TrackInfoListDTO();
        trackInfoListDTO.setList(List.of(new TrackInfoDTO("10.03.2025, 12:00", "WAITING")));

        deliveryHistoryService.updateDeliveryHistory(
                trackParcel,
                GlobalStatus.DELIVERED,
                GlobalStatus.WAITING_FOR_CUSTOMER,
                trackInfoListDTO
        );

        verify(deliveryMetricsRollbackService).rollbackFinalStatusMetrics(history, trackParcel, GlobalStatus.DELIVERED);
    }

    /**
     * Убеждаемся, что при первом сохранении трека с финальным статусом
     * уведомление в Telegram не отправляется.
     */
    @Test
    void updateDeliveryHistory_InitialFinalStatus_DoesNotSendNotification() {
        TrackParcel trackParcel = buildParcelWithCustomer(2L);

        when(deliveryHistoryRepository.findByTrackParcelId(trackParcel.getId())).thenReturn(Optional.empty());
        when(typeDefinitionTrackPostService.detectPostalService(anyString())).thenReturn(PostalServiceType.UNKNOWN);
        when(statusTrackService.setStatus(anyList())).thenReturn(GlobalStatus.DELIVERED);
        when(subscriptionService.isFeatureEnabled(trackParcel.getStore().getOwner().getId(), FeatureKey.TELEGRAM_NOTIFICATIONS))
                .thenReturn(true);
        when(deliveryHistoryRepository.save(any(DeliveryHistory.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TrackInfoListDTO trackInfoListDTO = buildDeliveredTrackInfo();

        deliveryHistoryService.updateDeliveryHistory(trackParcel, null, GlobalStatus.DELIVERED, trackInfoListDTO);

        verify(telegramNotificationService, never()).sendStatusUpdate(any(TrackParcel.class), any(GlobalStatus.class));
        verify(customerNotificationLogRepository, never())
                .existsByParcelIdAndStatusAndNotificationType(anyLong(), any(), any());
    }

    /**
     * Проверяет, что при переходе из промежуточного статуса в финальный уведомление отправляется
     * и фиксируется в журнале отправки.
     */
    @Test
    void updateDeliveryHistory_TransitionToFinalStatus_SendsNotification() {
        TrackParcel trackParcel = buildParcelWithCustomer(3L);

        when(deliveryHistoryRepository.findByTrackParcelId(trackParcel.getId())).thenReturn(Optional.empty());
        when(typeDefinitionTrackPostService.detectPostalService(anyString())).thenReturn(PostalServiceType.UNKNOWN);
        when(statusTrackService.setStatus(anyList())).thenReturn(GlobalStatus.DELIVERED);
        when(subscriptionService.isFeatureEnabled(trackParcel.getStore().getOwner().getId(), FeatureKey.TELEGRAM_NOTIFICATIONS))
                .thenReturn(true);
        when(customerNotificationLogRepository.existsByParcelIdAndStatusAndNotificationType(
                trackParcel.getId(),
                GlobalStatus.DELIVERED,
                NotificationType.INSTANT
        )).thenReturn(false);
        when(deliveryHistoryRepository.save(any(DeliveryHistory.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(telegramNotificationService.sendStatusUpdate(trackParcel, GlobalStatus.DELIVERED)).thenReturn(true);

        TrackInfoListDTO trackInfoListDTO = buildDeliveredTrackInfo();

        deliveryHistoryService.updateDeliveryHistory(
                trackParcel,
                GlobalStatus.IN_TRANSIT,
                GlobalStatus.DELIVERED,
                trackInfoListDTO
        );

        verify(telegramNotificationService).sendStatusUpdate(eq(trackParcel), eq(GlobalStatus.DELIVERED));
        verify(customerNotificationLogRepository).save(any(CustomerNotificationLog.class));
    }

    /**
     * Убеждаемся, что при неудачной отправке уведомления запись в журнале не создаётся.
     */
    @Test
    void updateDeliveryHistory_NotificationFailed_DoesNotPersistLog() {
        TrackParcel trackParcel = buildParcelWithCustomer(4L);

        when(deliveryHistoryRepository.findByTrackParcelId(trackParcel.getId())).thenReturn(Optional.empty());
        when(typeDefinitionTrackPostService.detectPostalService(anyString())).thenReturn(PostalServiceType.UNKNOWN);
        when(statusTrackService.setStatus(anyList())).thenReturn(GlobalStatus.DELIVERED);
        when(subscriptionService.isFeatureEnabled(trackParcel.getStore().getOwner().getId(), FeatureKey.TELEGRAM_NOTIFICATIONS))
                .thenReturn(true);
        when(customerNotificationLogRepository.existsByParcelIdAndStatusAndNotificationType(
                trackParcel.getId(),
                GlobalStatus.DELIVERED,
                NotificationType.INSTANT
        )).thenReturn(false);
        when(deliveryHistoryRepository.save(any(DeliveryHistory.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(telegramNotificationService.sendStatusUpdate(trackParcel, GlobalStatus.DELIVERED)).thenReturn(false);

        TrackInfoListDTO trackInfoListDTO = buildDeliveredTrackInfo();

        deliveryHistoryService.updateDeliveryHistory(
                trackParcel,
                GlobalStatus.IN_TRANSIT,
                GlobalStatus.DELIVERED,
                trackInfoListDTO
        );

        verify(telegramNotificationService).sendStatusUpdate(eq(trackParcel), eq(GlobalStatus.DELIVERED));
        verify(customerNotificationLogRepository, never()).save(any(CustomerNotificationLog.class));
    }

    /**
     * Создаёт тестовую посылку с привязанным магазином и покупателем.
     */
    private TrackParcel buildParcelWithCustomer(Long parcelId) {
        TrackParcel trackParcel = new TrackParcel();
        trackParcel.setId(parcelId);
        trackParcel.setNumber("RB123456789BY");
        trackParcel.setStatus(GlobalStatus.IN_TRANSIT);

        User owner = new User();
        owner.setId(500L);
        owner.setTimeZone("UTC");

        Store store = new Store();
        store.setId(200L);
        store.setName("Test Store");
        store.setOwner(owner);
        trackParcel.setStore(store);
        trackParcel.setUser(owner);

        Customer customer = new Customer();
        customer.setId(300L);
        customer.setTelegramChatId(123456L);
        trackParcel.setCustomer(customer);

        return trackParcel;
    }

    /**
     * Формирует список трекинг-событий с финальным статусом доставки.
     */
    private TrackInfoListDTO buildDeliveredTrackInfo() {
        TrackInfoListDTO trackInfoListDTO = new TrackInfoListDTO();
        trackInfoListDTO.setList(List.of(new TrackInfoDTO("10.03.2025, 12:00", "DELIVERED")));
        return trackInfoListDTO;
    }
}
