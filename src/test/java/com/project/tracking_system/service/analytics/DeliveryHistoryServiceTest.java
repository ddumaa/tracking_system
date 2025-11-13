package com.project.tracking_system.service.analytics;

import com.project.tracking_system.dto.TrackInfoDTO;
import com.project.tracking_system.dto.TrackInfoListDTO;
import com.project.tracking_system.entity.*;
import com.project.tracking_system.model.subscription.FeatureKey;
import com.project.tracking_system.repository.*;
import com.project.tracking_system.service.SubscriptionService;
import com.project.tracking_system.service.customer.CustomerService;
import com.project.tracking_system.service.customer.CustomerStatsService;
import com.project.tracking_system.service.order.ReturnTrackingEvent;
import com.project.tracking_system.service.order.ReturnTrackingEventHandler;
import com.project.tracking_system.service.order.ReturnTrackingEventType;
import com.project.tracking_system.service.track.StatusTrackService;
import com.project.tracking_system.service.track.TypeDefinitionTrackPostService;
import org.mockito.ArgumentCaptor;
import com.project.tracking_system.service.telegram.TelegramNotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.project.tracking_system.service.order.OrderEpisodeLifecycleService;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
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
    @Mock
    private ReturnTrackingEventHandler returnTrackingEventHandler;

    @InjectMocks
    private DeliveryHistoryService deliveryHistoryService;

    @BeforeEach
    void setupEpisodes() {
        lenient().doAnswer(invocation -> {
            TrackParcel parcel = invocation.getArgument(0);
            OrderEpisode episode = parcel.getEpisode();
            if (episode == null) {
                episode = new OrderEpisode();
                parcel.setEpisode(episode);
            }
            return episode;
        }).when(orderEpisodeLifecycleService).ensureEpisode(any());

        lenient().doAnswer(invocation -> {
            TrackParcel parcel = invocation.getArgument(0);
            if (parcel.getEpisode() == null) {
                parcel.setEpisode(new OrderEpisode());
            }
            return null;
        }).when(orderEpisodeLifecycleService).syncEpisodeCustomer(any());

        lenient().when(storeAnalyticsRepository.findByStoreId(anyLong()))
                .thenReturn(Optional.of(new StoreStatistics()));
        lenient().when(storeAnalyticsRepository.save(any(StoreStatistics.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(postalServiceStatisticsRepository.findByStoreIdAndPostalServiceType(anyLong(), any()))
                .thenReturn(Optional.of(new PostalServiceStatistics()));
        lenient().when(postalServiceStatisticsRepository.save(any(PostalServiceStatistics.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(storeAnalyticsRepository.incrementDelivered(anyLong(), anyInt(), any(), any()))
                .thenReturn(1);
        lenient().when(storeAnalyticsRepository.incrementReturned(anyLong(), anyInt(), any(), any()))
                .thenReturn(1);
        lenient().when(postalServiceStatisticsRepository.incrementDelivered(anyLong(), any(), anyInt(), any(), any()))
                .thenReturn(1);
        lenient().when(postalServiceStatisticsRepository.incrementReturned(anyLong(), any(), anyInt(), any(), any()))
                .thenReturn(1);
        lenient().when(storeDailyStatisticsRepository.incrementDelivered(anyLong(), any(), anyInt(), any(), any()))
                .thenReturn(1);
        lenient().when(storeDailyStatisticsRepository.incrementReturned(anyLong(), any(), anyInt(), any(), any()))
                .thenReturn(1);
        lenient().when(postalServiceDailyStatisticsRepository.incrementDelivered(anyLong(), any(), any(), anyInt(), any(), any()))
                .thenReturn(1);
        lenient().when(postalServiceDailyStatisticsRepository.incrementReturned(anyLong(), any(), any(), anyInt(), any(), any()))
                .thenReturn(1);
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
        lenient().when(typeDefinitionTrackPostService.detectPostalService(anyString())).thenReturn(PostalServiceType.BELPOST);
        lenient().when(statusTrackService.setStatus(anyList())).thenReturn(GlobalStatus.WAITING_FOR_CUSTOMER);
        lenient().when(trackParcelRepository.save(trackParcel)).thenReturn(trackParcel);
        lenient().when(customerStatsService.incrementSent(any(Customer.class))).thenAnswer(invocation -> invocation.getArgument(0));

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
        lenient().when(typeDefinitionTrackPostService.detectPostalService(anyString())).thenReturn(PostalServiceType.UNKNOWN);
        lenient().when(statusTrackService.setStatus(anyList())).thenReturn(GlobalStatus.DELIVERED);
        lenient().when(subscriptionService.isFeatureEnabled(trackParcel.getStore().getOwner().getId(), FeatureKey.TELEGRAM_NOTIFICATIONS))
                .thenReturn(true);
        lenient().when(deliveryHistoryRepository.save(any(DeliveryHistory.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TrackInfoListDTO trackInfoListDTO = buildDeliveredTrackInfo();

        deliveryHistoryService.updateDeliveryHistory(trackParcel, null, GlobalStatus.DELIVERED, trackInfoListDTO);

        verify(telegramNotificationService, never()).sendStatusUpdate(any(TrackParcel.class), any(GlobalStatus.class));
        verify(customerNotificationLogRepository, never())
                .existsByParcelIdAndStatusAndNotificationType(anyLong(), any(), any());
    }

    @Test
    void updateDeliveryHistory_WhenReturnArrives_PublishesTrackingEvent() {
        TrackParcel trackParcel = buildParcelWithCustomer(6L);

        when(deliveryHistoryRepository.findByTrackParcelId(trackParcel.getId())).thenReturn(Optional.empty());
        lenient().when(typeDefinitionTrackPostService.detectPostalService(anyString())).thenReturn(PostalServiceType.BELPOST);
        lenient().when(statusTrackService.setStatus(anyList())).thenReturn(GlobalStatus.RETURN_PENDING_PICKUP);
        lenient().when(deliveryHistoryRepository.save(any(DeliveryHistory.class))).thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(subscriptionService.isFeatureEnabled(anyLong(), any())).thenReturn(false);

        TrackInfoDTO dto = new TrackInfoDTO("10.03.2025, 12:00", "Возврат прибыл");
        TrackInfoListDTO trackInfoListDTO = new TrackInfoListDTO(List.of(dto));

        deliveryHistoryService.updateDeliveryHistory(
                trackParcel,
                GlobalStatus.RETURN_IN_PROGRESS,
                GlobalStatus.RETURN_PENDING_PICKUP,
                trackInfoListDTO
        );

        ArgumentCaptor<ReturnTrackingEvent> captor = ArgumentCaptor.forClass(ReturnTrackingEvent.class);
        verify(returnTrackingEventHandler).handle(captor.capture());
        ReturnTrackingEvent event = captor.getValue();
        assertEquals(ReturnTrackingEventType.RETURN_ARRIVED_TO_STORE, event.type());
        assertEquals(trackParcel, event.parcel());
    }

    @Test
    void updateDeliveryHistory_WhenReturnPickedUp_PublishesTrackingEvent() {
        TrackParcel trackParcel = buildParcelWithCustomer(7L);

        when(deliveryHistoryRepository.findByTrackParcelId(trackParcel.getId())).thenReturn(Optional.empty());
        lenient().when(typeDefinitionTrackPostService.detectPostalService(anyString())).thenReturn(PostalServiceType.BELPOST);
        lenient().when(statusTrackService.setStatus(anyList())).thenReturn(GlobalStatus.RETURNED);
        lenient().when(deliveryHistoryRepository.save(any(DeliveryHistory.class))).thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(subscriptionService.isFeatureEnabled(anyLong(), any())).thenReturn(false);

        TrackInfoDTO dto = new TrackInfoDTO("11.03.2025, 14:30", "Возврат получен");
        TrackInfoListDTO trackInfoListDTO = new TrackInfoListDTO(List.of(dto));

        deliveryHistoryService.updateDeliveryHistory(
                trackParcel,
                GlobalStatus.RETURN_PENDING_PICKUP,
                GlobalStatus.RETURNED,
                trackInfoListDTO
        );

        ArgumentCaptor<ReturnTrackingEvent> captor = ArgumentCaptor.forClass(ReturnTrackingEvent.class);
        verify(returnTrackingEventHandler).handle(captor.capture());
        ReturnTrackingEvent event = captor.getValue();
        assertEquals(ReturnTrackingEventType.RETURN_PICKED_UP_BY_STORE, event.type());
        assertEquals(trackParcel, event.parcel());
    }

    @Test
    void updateDeliveryHistory_WhenExchangeDelivered_PublishesTrackingEvent() {
        TrackParcel trackParcel = buildParcelWithCustomer(8L);
        trackParcel.setExchange(true);

        when(deliveryHistoryRepository.findByTrackParcelId(trackParcel.getId())).thenReturn(Optional.empty());
        lenient().when(typeDefinitionTrackPostService.detectPostalService(anyString())).thenReturn(PostalServiceType.BELPOST);
        lenient().when(statusTrackService.setStatus(anyList())).thenReturn(GlobalStatus.DELIVERED);
        lenient().when(deliveryHistoryRepository.save(any(DeliveryHistory.class))).thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(subscriptionService.isFeatureEnabled(anyLong(), any())).thenReturn(false);

        TrackInfoDTO dto = new TrackInfoDTO("12.03.2025, 09:15", "Обмен доставлен");
        TrackInfoListDTO trackInfoListDTO = new TrackInfoListDTO(List.of(dto));

        deliveryHistoryService.updateDeliveryHistory(
                trackParcel,
                GlobalStatus.IN_TRANSIT,
                GlobalStatus.DELIVERED,
                trackInfoListDTO
        );

        ArgumentCaptor<ReturnTrackingEvent> captor = ArgumentCaptor.forClass(ReturnTrackingEvent.class);
        verify(returnTrackingEventHandler).handle(captor.capture());
        ReturnTrackingEvent event = captor.getValue();
        assertEquals(ReturnTrackingEventType.EXCHANGE_DELIVERED_TO_CUSTOMER, event.type());
        assertEquals(trackParcel, event.parcel());
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
     * Убеждаемся, что отсутствие статистики по магазину не блокирует удаление трека.
     */
    @Test
    void handleTrackParcelBeforeDelete_NoStoreStats_SkipsAdjustments() {
        TrackParcel trackParcel = buildParcelWithCustomer(5L);
        trackParcel.setIncludedInStatistics(false);

        when(storeAnalyticsRepository.findByStoreId(trackParcel.getStore().getId())).thenReturn(Optional.empty());

        assertDoesNotThrow(() -> deliveryHistoryService.handleTrackParcelBeforeDelete(trackParcel));

        verify(customerService).rollbackStatsOnTrackDelete(trackParcel);
        verify(storeAnalyticsRepository).findByStoreId(trackParcel.getStore().getId());
        verifyNoInteractions(storeDailyStatisticsRepository, postalServiceStatisticsRepository, postalServiceDailyStatisticsRepository);
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
