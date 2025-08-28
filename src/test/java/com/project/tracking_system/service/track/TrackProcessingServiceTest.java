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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.ZoneId;
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
                trackStatisticsUpdater
        );
    }

    /**
     * Убеждаемся, что при отсутствии статусов
     * предварительно зарегистрированный трек остаётся без изменений.
     */
    @Test
    void preRegisteredWithoutStatuses_noChanges() {
        // подготавливаем трек
        TrackParcel parcel = new TrackParcel();
        parcel.setNumber("AB123");
        parcel.setStatus(GlobalStatus.PRE_REGISTERED);
        Store store = new Store();
        store.setId(1L);
        parcel.setStore(store);
        when(trackParcelRepository.findByNumberAndUserId("AB123", 5L)).thenReturn(parcel);

        TrackInfoListDTO info = new TrackInfoListDTO();

        trackProcessingService.save("AB123", info, 1L, 5L, null);

        assertEquals(GlobalStatus.PRE_REGISTERED, parcel.getStatus());
        assertTrue(parcel.isPreRegistered());
        verify(trackParcelRepository, never()).save(any());
        verify(statusTrackService, never()).setStatus(any());
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
        when(trackParcelRepository.findByNumberAndUserId("AB123", 5L)).thenReturn(parcel);

        TrackInfoDTO dto = new TrackInfoDTO("07.01.2025, 12:00", "Вручено");
        TrackInfoListDTO info = new TrackInfoListDTO(List.of(dto));
        when(statusTrackService.setStatus(any())).thenReturn(GlobalStatus.DELIVERED);
        when(userService.getUserZone(5L)).thenReturn(ZoneId.of("UTC"));
        when(trackParcelRepository.save(any())).thenReturn(parcel);
        when(deliveryHistoryService.hasFinalStatus(any())).thenReturn(false);

        trackProcessingService.save("AB123", info, 1L, 5L, null);

        assertEquals(GlobalStatus.DELIVERED, parcel.getStatus());
        assertFalse(parcel.isPreRegistered());
        verify(trackParcelRepository).save(parcel);
    }
}

