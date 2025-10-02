package com.project.tracking_system.service.track;

import com.project.tracking_system.dto.TrackInfoDTO;
import com.project.tracking_system.dto.TrackInfoListDTO;
import com.project.tracking_system.entity.GlobalStatus;
import com.project.tracking_system.entity.Store;
import com.project.tracking_system.entity.TrackParcel;
import com.project.tracking_system.repository.StoreRepository;
import com.project.tracking_system.repository.TrackParcelRepository;
import com.project.tracking_system.repository.UserRepository;
import com.project.tracking_system.service.SubscriptionService;
import com.project.tracking_system.service.analytics.DeliveryHistoryService;
import com.project.tracking_system.service.analytics.TrackStatisticsUpdater;
import com.project.tracking_system.service.customer.CustomerService;
import com.project.tracking_system.service.customer.CustomerStatsService;
import com.project.tracking_system.service.user.UserService;
import com.project.tracking_system.service.track.TrackStatusEventService;
import com.project.tracking_system.service.order.OrderEpisodeLifecycleService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Тесты сервиса {@link TrackProcessingService} для проверки обработки
 * предварительно зарегистрированных треков.
 */
@ExtendWith(MockitoExtension.class)
class TrackProcessingServiceTest {

    @Mock
    private TypeDefinitionTrackPostService typeDefinitionTrackPostService;
    @Mock
    private StatusTrackService statusTrackService;
    @Mock
    private SubscriptionService subscriptionService;
    @Mock
    private DeliveryHistoryService deliveryHistoryService;
    @Mock
    private CustomerService customerService;
    @Mock
    private CustomerStatsService customerStatsService;
    @Mock
    private UserService userService;
    @Mock
    private StoreRepository storeRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private TrackParcelRepository trackParcelRepository;
    @Mock
    private TrackStatisticsUpdater trackStatisticsUpdater;
    @Mock
    private TrackStatusEventService trackStatusEventService;
    @Mock
    private OrderEpisodeLifecycleService orderEpisodeLifecycleService;

    private TrackProcessingService trackProcessingService;

    @BeforeEach
    void init() {
        trackProcessingService = new TrackProcessingService(
                typeDefinitionTrackPostService,
                statusTrackService,
                subscriptionService,
                deliveryHistoryService,
                customerService,
                customerStatsService,
                userService,
                storeRepository,
                userRepository,
                trackParcelRepository,
                trackStatisticsUpdater,
                trackStatusEventService,
                orderEpisodeLifecycleService
        );
        doAnswer(invocation -> {
            TrackParcel parcel = invocation.getArgument(0);
            if (parcel.getEpisode() == null) {
                parcel.setEpisode(new com.project.tracking_system.entity.OrderEpisode());
            }
            return null;
        }).when(orderEpisodeLifecycleService).syncEpisodeCustomer(any());
    }

    /**
     * Убеждаемся, что при отсутствии статусов
     * предварительно зарегистрированный трек обновляет только
     * отметку времени последнего обновления.
     */
    @Test
    void preRegisteredWithoutStatuses_updatesLastUpdateOnly() {
        // подготавливаем трек
        TrackParcel parcel = new TrackParcel();
        parcel.setNumber("AB123");
        parcel.setStatus(GlobalStatus.PRE_REGISTERED);
        Store store = new Store();
        store.setId(1L);
        parcel.setStore(store);
        parcel.setEpisode(new com.project.tracking_system.entity.OrderEpisode());
        ZonedDateTime previousUpdate = ZonedDateTime.now(ZoneOffset.UTC).minusHours(3);
        parcel.setLastUpdate(previousUpdate);
        when(trackParcelRepository.findByNumberAndUserId("AB123", 5L)).thenReturn(parcel);
        when(trackParcelRepository.save(parcel)).thenReturn(parcel);

        TrackInfoListDTO info = new TrackInfoListDTO();

        trackProcessingService.save("AB123", info, 1L, 5L, null);

        assertEquals(GlobalStatus.PRE_REGISTERED, parcel.getStatus());
        assertTrue(parcel.isPreRegistered());
        assertTrue(parcel.getLastUpdate().isAfter(previousUpdate));
        verify(trackParcelRepository).save(parcel);
        verify(statusTrackService, never()).setStatus(any());
        verify(trackStatusEventService, never()).replaceEvents(any(), any(), any());
    }

    /**
     * Проверяем, что при наличии статусов
     * предварительно зарегистрированный трек обновляется,
     * а флаг preRegistered снимается.
     */
    @Test
    void preRegisteredWithStatuses_updatesTrack() {
        TrackParcel parcel = new TrackParcel();
        parcel.setNumber("AB123");
        parcel.setStatus(GlobalStatus.PRE_REGISTERED);
        Store store = new Store();
        store.setId(1L);
        parcel.setStore(store);
        parcel.setEpisode(new com.project.tracking_system.entity.OrderEpisode());
        when(trackParcelRepository.findByNumberAndUserId("AB123", 5L)).thenReturn(parcel);

        TrackInfoDTO dto = new TrackInfoDTO("07.01.2025, 12:00", "Вручено");
        TrackInfoListDTO info = new TrackInfoListDTO(List.of(dto));
        when(statusTrackService.setStatus(any())).thenReturn(GlobalStatus.DELIVERED);
        when(userService.getUserZone(5L)).thenReturn(ZoneId.of("UTC"));
        when(trackParcelRepository.save(any())).thenReturn(parcel);

        trackProcessingService.save("AB123", info, 1L, 5L, null);

        assertEquals(GlobalStatus.DELIVERED, parcel.getStatus());
        assertFalse(parcel.isPreRegistered());
        verify(trackParcelRepository).save(parcel);
        verify(trackStatusEventService).replaceEvents(eq(parcel), eq(info.getList()), eq(ZoneId.of("UTC")));
    }

    /**
     * При отмене регистрации эпизод должен быть закрыт с исходом CANCELLED.
     */
    @Test
    void registrationCancelled_registersFinalOutcome() {
        TrackParcel parcel = new TrackParcel();
        parcel.setNumber("AB123");
        parcel.setStatus(GlobalStatus.PRE_REGISTERED);
        Store store = new Store();
        store.setId(1L);
        parcel.setStore(store);
        parcel.setEpisode(new com.project.tracking_system.entity.OrderEpisode());
        when(trackParcelRepository.findByNumberAndUserId("AB123", 5L)).thenReturn(parcel);

        TrackInfoDTO dto = new TrackInfoDTO("07.01.2025, 12:00", "Регистрация отменена");
        TrackInfoListDTO info = new TrackInfoListDTO(List.of(dto));
        when(statusTrackService.setStatus(any())).thenReturn(GlobalStatus.REGISTRATION_CANCELLED);
        when(userService.getUserZone(5L)).thenReturn(ZoneId.of("UTC"));
        when(trackParcelRepository.save(any())).thenReturn(parcel);

        trackProcessingService.save("AB123", info, 1L, 5L, null);

        verify(orderEpisodeLifecycleService).registerFinalOutcome(parcel, GlobalStatus.REGISTRATION_CANCELLED);
        verify(orderEpisodeLifecycleService, never()).reopenEpisode(any());
    }

    /**
     * Возврат из состояния отмены регистрации должен переоткрывать эпизод.
     */
    @Test
    void statusRestoredAfterCancellation_reopensEpisode() {
        TrackParcel parcel = new TrackParcel();
        parcel.setNumber("AB123");
        parcel.setStatus(GlobalStatus.REGISTRATION_CANCELLED);
        Store store = new Store();
        store.setId(1L);
        parcel.setStore(store);
        parcel.setEpisode(new com.project.tracking_system.entity.OrderEpisode());
        when(trackParcelRepository.findByNumberAndUserId("AB123", 5L)).thenReturn(parcel);

        TrackInfoDTO dto = new TrackInfoDTO("07.01.2025, 12:00", "В пути");
        TrackInfoListDTO info = new TrackInfoListDTO(List.of(dto));
        when(statusTrackService.setStatus(any())).thenReturn(GlobalStatus.IN_TRANSIT);
        when(userService.getUserZone(5L)).thenReturn(ZoneId.of("UTC"));
        when(trackParcelRepository.save(any())).thenReturn(parcel);

        trackProcessingService.save("AB123", info, 1L, 5L, null);

        verify(orderEpisodeLifecycleService).reopenEpisode(parcel);
        verify(orderEpisodeLifecycleService, never())
                .registerFinalOutcome(eq(parcel), eq(GlobalStatus.REGISTRATION_CANCELLED));
    }
}
