package com.project.tracking_system.service.track;

import com.project.tracking_system.controller.WebSocketController;
import com.project.tracking_system.dto.TrackingResultAdd;
import com.project.tracking_system.entity.DeliveryHistory;
import com.project.tracking_system.entity.GlobalStatus;
import com.project.tracking_system.entity.PostalServiceType;
import com.project.tracking_system.entity.Store;
import com.project.tracking_system.entity.TrackParcel;
import com.project.tracking_system.entity.UpdateResult;
import com.project.tracking_system.repository.StoreRepository;
import com.project.tracking_system.repository.TrackParcelRepository;
import com.project.tracking_system.service.SubscriptionService;
import com.project.tracking_system.service.admin.ApplicationSettingsService;
import com.project.tracking_system.service.belpost.BelPostTrackQueueService;
import com.project.tracking_system.service.track.ProgressAggregatorService;
import com.project.tracking_system.service.track.TrackParcelService;
import com.project.tracking_system.service.track.TrackUpdateDispatcherService;
import com.project.tracking_system.service.track.TrackUploadGroupingService;
import com.project.tracking_system.service.user.UserService;
import com.project.tracking_system.service.track.TrackUpdateService;
import com.project.tracking_system.service.track.TrackConstants;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;

/**
 * Unit tests for {@link TrackUpdateService} asynchronous update methods.
 */
@ExtendWith(MockitoExtension.class)
class TrackUpdateServiceTest {

    @Mock
    private WebSocketController webSocketController;
    @Mock
    private SubscriptionService subscriptionService;
    @Mock
    private StoreRepository storeRepository;
    @Mock
    private TrackParcelRepository trackParcelRepository;
    @Mock
    private TrackParcelService trackParcelService;
    @Mock
    private TrackUploadGroupingService groupingService;
    @Mock
    private TrackUpdateDispatcherService dispatcherService;
    @Mock
    private BelPostTrackQueueService belPostTrackQueueService;
    @Mock
    private ProgressAggregatorService progressAggregatorService;
    @Mock
    private TrackingResultCacheService trackingResultCacheService;
    @Mock
    private ApplicationSettingsService applicationSettingsService;
    @Mock
    private UserService userService;

    @Spy
    @InjectMocks
    private TrackUpdateService service;

    private TrackParcel first;
    private TrackParcel second;

    @BeforeEach
    void setUp() {
        Store store = new Store();
        store.setId(1L);

        first = buildParcel("T1", store, PostalServiceType.BELPOST);
        second = buildParcel("T2", store, PostalServiceType.EVROPOST);
    }

    /**
     * Проверяем, что метод обрабатывает все треки и отправляет уведомление с корректным количеством обновлений.
     */
    @Test
    void processAllTrackUpdatesAsync_SendsUpdateCount() {
        List<TrackingResultAdd> results = List.of(
                new TrackingResultAdd("T1", "ok"),
                new TrackingResultAdd("T2", TrackConstants.NO_DATA_STATUS)
        );
        doReturn(results).when(service).process(anyList(), eq(1L));

        service.processAllTrackUpdatesAsync(1L, List.of(first, second));

        ArgumentCaptor<UpdateResult> captor = ArgumentCaptor.forClass(UpdateResult.class);
        verify(webSocketController).sendDetailUpdateStatus(eq(1L), captor.capture());
        UpdateResult updateResult = captor.getValue();
        assertEquals(1, updateResult.getUpdateCount());
        assertEquals(2, updateResult.getRequestedCount());
    }

    /**
     * Проверяем, что при обновлении выбранных треков счётчик увеличивается и отправляется уведомление.
     */
    @Test
    void processTrackUpdatesAsync_IncrementsAndNotifies() {
        List<TrackingResultAdd> results = List.of(
                new TrackingResultAdd("T1", "ok"),
                new TrackingResultAdd("T2", "ok")
        );
        doReturn(results).when(service).process(anyList(), eq(1L));

        service.processTrackUpdatesAsync(1L, List.of(first, second), 3, 1);

        verify(trackParcelService).incrementUpdateCount(1L, 2);
        ArgumentCaptor<UpdateResult> captor = ArgumentCaptor.forClass(UpdateResult.class);
        verify(webSocketController).sendDetailUpdateStatus(eq(1L), captor.capture());
        UpdateResult updateResult = captor.getValue();
        assertEquals(2, updateResult.getUpdateCount());
        assertEquals(3, updateResult.getRequestedCount());
    }

    /**
     * Создаёт тестовую посылку с минимально необходимыми данными.
     */
    private static TrackParcel buildParcel(String number, Store store, PostalServiceType postalServiceType) {
        TrackParcel parcel = new TrackParcel();
        parcel.setNumber(number);
        parcel.setStatus(GlobalStatus.IN_TRANSIT);
        parcel.setStore(store);
        DeliveryHistory history = new DeliveryHistory();
        history.setPostalService(postalServiceType);
        parcel.setDeliveryHistory(history);
        return parcel;
    }
}

