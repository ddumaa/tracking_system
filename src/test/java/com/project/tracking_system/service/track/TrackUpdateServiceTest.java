package com.project.tracking_system.service.track;

import com.project.tracking_system.controller.WebSocketController;
import com.project.tracking_system.entity.GlobalStatus;
import com.project.tracking_system.entity.Store;
import com.project.tracking_system.entity.TrackParcel;
import com.project.tracking_system.entity.UpdateResult;
import com.project.tracking_system.model.subscription.FeatureKey;
import com.project.tracking_system.repository.StoreRepository;
import com.project.tracking_system.repository.TrackParcelRepository;
import com.project.tracking_system.service.SubscriptionService;
import com.project.tracking_system.dto.TrackingResultAdd;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Тесты для {@link TrackUpdateService}.
 * Проверяем фильтрацию финальных статусов, лимиты подписки и отправку уведомлений.
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

    private TrackUpdateService service;

    @BeforeEach
    void setUp() {
        service = new TrackUpdateService(
                webSocketController,
                subscriptionService,
                storeRepository,
                trackParcelRepository,
                trackParcelService,
                groupingService,
                dispatcherService
        );
    }
    /**
     * Проверяем, что при отключённой функции массового обновления
     * сервис сообщает об ошибке и не запускает процесс обновления.
     */
    @Test
    void updateAllParcels_FeatureDisabled_SendsError() {
        when(subscriptionService.isFeatureEnabled(1L, FeatureKey.BULK_UPDATE)).thenReturn(false);

        UpdateResult result = service.updateAllParcels(1L);

        assertFalse(result.isSuccess());
        verify(webSocketController).sendUpdateStatus(eq(1L), anyString(), eq(false));
        verifyNoInteractions(groupingService, dispatcherService);
    }

    /**
     * Проверяем фильтрацию финальных статусов и корректное уведомление
     * после асинхронной обработки всех посылок.
     */
    @Test
    void updateAllParcels_FiltersFinalStatusesAndSendsNotification() throws Exception {
        when(subscriptionService.isFeatureEnabled(1L, FeatureKey.BULK_UPDATE)).thenReturn(true);
        when(storeRepository.countByOwnerId(1L)).thenReturn(1);

        TrackParcel finished = buildParcel("F1", GlobalStatus.DELIVERED, 11L);
        TrackParcel active = buildParcel("A1", GlobalStatus.IN_TRANSIT, 22L);
        when(trackParcelRepository.findByUserId(1L)).thenReturn(List.of(finished, active));

        List<TrackingResultAdd> results = List.of(new TrackingResultAdd("A1", "ok"));
        when(groupingService.group(anyList())).thenReturn(java.util.Collections.emptyMap());
        when(dispatcherService.dispatch(anyMap(), eq(1L))).thenReturn(results);

        CountDownLatch latch = new CountDownLatch(1);
        doAnswer(inv -> { latch.countDown(); return null; })
                .when(webSocketController).sendDetailUpdateStatus(eq(1L), any(UpdateResult.class));

        UpdateResult start = service.updateAllParcels(1L);
        assertTrue(start.isSuccess());
        assertEquals(1, start.getUpdateCount());
        assertEquals(2, start.getRequestedCount());

        assertTrue(latch.await(1, TimeUnit.SECONDS), "Notification should be sent");
        verify(groupingService).group(anyList());
        verify(dispatcherService).dispatch(anyMap(), eq(1L));
        verify(webSocketController).sendUpdateStatus(eq(1L), contains("запущено"), eq(true));
        verify(webSocketController).sendDetailUpdateStatus(eq(1L), any(UpdateResult.class));
    }

    /**
     * Проверяем соблюдение лимита обновлений и уведомление после завершения.
     */
    @Test
    void updateSelectedParcels_RespectsLimitAndSendsNotification() throws Exception {
        TrackParcel finalParcel = buildParcel("F1", GlobalStatus.DELIVERED, 11L);
        TrackParcel first = buildParcel("A1", GlobalStatus.IN_TRANSIT, 22L);
        TrackParcel second = buildParcel("A2", GlobalStatus.IN_TRANSIT, 33L);
        when(trackParcelRepository.findByNumberInAndUserId(anyList(), eq(1L)))
                .thenReturn(List.of(finalParcel, first, second));
        when(subscriptionService.canUpdateTracks(1L, 2)).thenReturn(1);

        List<TrackingResultAdd> resultList = List.of(new TrackingResultAdd("A1", "ok"));
        when(groupingService.group(anyList())).thenReturn(java.util.Collections.emptyMap());
        when(dispatcherService.dispatch(anyMap(), eq(1L))).thenReturn(resultList);

        CountDownLatch latch = new CountDownLatch(1);
        doAnswer(inv -> { latch.countDown(); return null; })
                .when(webSocketController).sendDetailUpdateStatus(eq(1L), any(UpdateResult.class));

        UpdateResult result = service.updateSelectedParcels(1L, List.of("F1", "A1", "A2"));
        assertTrue(result.isSuccess());
        assertEquals(1, result.getUpdateCount());

        assertTrue(latch.await(1, TimeUnit.SECONDS), "Notification should be sent");
        verify(groupingService).group(anyList());
        verify(dispatcherService).dispatch(anyMap(), eq(1L));
        verify(trackParcelService).incrementUpdateCount(1L, 1);
        verify(webSocketController).sendDetailUpdateStatus(eq(1L), any(UpdateResult.class));
        verify(webSocketController, never()).sendUpdateStatus(eq(1L), anyString(), anyBoolean());
    }

    private static TrackParcel buildParcel(String number, GlobalStatus status, Long storeId) {
        TrackParcel parcel = new TrackParcel();
        parcel.setNumber(number);
        parcel.setStatus(status);
        Store store = new Store();
        store.setId(storeId);
        parcel.setStore(store);
        parcel.setTimestamp(ZonedDateTime.now());
        return parcel;
    }
}
