package com.project.tracking_system.service.track;

import com.project.tracking_system.controller.WebSocketController;
import com.project.tracking_system.dto.TrackInfoDTO;
import com.project.tracking_system.dto.TrackInfoListDTO;
import com.project.tracking_system.dto.TrackParcelDTO;
import com.project.tracking_system.entity.GlobalStatus;
import com.project.tracking_system.entity.Store;
import com.project.tracking_system.entity.TrackParcel;
import com.project.tracking_system.entity.UpdateResult;
import com.project.tracking_system.model.subscription.FeatureKey;
import com.project.tracking_system.repository.StoreRepository;
import com.project.tracking_system.repository.TrackParcelRepository;
import com.project.tracking_system.service.SubscriptionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ConcurrentTaskExecutor;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
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
    private TrackProcessingService trackProcessingService;
    @Mock
    private SubscriptionService subscriptionService;
    @Mock
    private StoreRepository storeRepository;
    @Mock
    private TrackParcelRepository trackParcelRepository;
    @Mock
    private TrackParcelService trackParcelService;

    private TaskExecutor executor;
    private TrackUpdateService service;

    @BeforeEach
    void setUp() {
        executor = new ConcurrentTaskExecutor(Executors.newFixedThreadPool(2));
        service = new TrackUpdateService(webSocketController,
                trackProcessingService,
                subscriptionService,
                storeRepository,
                trackParcelRepository,
                trackParcelService,
                executor);
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
        verifyNoInteractions(trackProcessingService);
    }

    /**
     * Проверяем фильтрацию финальных статусов и корректное уведомление
     * после асинхронной обработки всех посылок.
     */
    @Test
    void updateAllParcels_FiltersFinalStatusesAndSendsNotification() throws Exception {
        when(subscriptionService.isFeatureEnabled(1L, FeatureKey.BULK_UPDATE)).thenReturn(true);
        when(storeRepository.countByOwnerId(1L)).thenReturn(1);

        List<TrackParcelDTO> parcels = List.of(
                new TrackParcelDTO(1L, "F1", GlobalStatus.DELIVERED.getDescription(), null, null, 11L),
                new TrackParcelDTO(2L, "A1", GlobalStatus.IN_TRANSIT.getDescription(), null, null, 22L)
        );
        when(trackParcelService.findAllByUserTracks(1L)).thenReturn(parcels);

        TrackInfoListDTO dto = new TrackInfoListDTO();
        dto.addTrackInfo(new TrackInfoDTO("t", "info"));
        when(trackProcessingService.processTrack(anyString(), anyLong(), eq(1L), eq(true)))
                .thenReturn(dto);

        CountDownLatch latch = new CountDownLatch(1);
        doAnswer(inv -> { latch.countDown(); return null; })
                .when(webSocketController).sendDetailUpdateStatus(eq(1L), any(UpdateResult.class));

        UpdateResult start = service.updateAllParcels(1L);
        assertTrue(start.isSuccess());
        assertEquals(1, start.getUpdateCount());
        assertEquals(2, start.getRequestedCount());

        assertTrue(latch.await(1, TimeUnit.SECONDS), "Notification should be sent");
        verify(trackProcessingService).processTrack("A1", 22L, 1L, true);
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

        TrackInfoListDTO dto = new TrackInfoListDTO();
        dto.addTrackInfo(new TrackInfoDTO("t", "info"));
        when(trackProcessingService.processTrack(anyString(), anyLong(), eq(1L), eq(true)))
                .thenReturn(dto);

        CountDownLatch latch = new CountDownLatch(1);
        doAnswer(inv -> { latch.countDown(); return null; })
                .when(webSocketController).sendDetailUpdateStatus(eq(1L), any(UpdateResult.class));

        UpdateResult result = service.updateSelectedParcels(1L, List.of("F1", "A1", "A2"));
        assertTrue(result.isSuccess());
        assertEquals(1, result.getUpdateCount());

        assertTrue(latch.await(1, TimeUnit.SECONDS), "Notification should be sent");
        verify(trackProcessingService).processTrack("A1", 22L, 1L, true);
        verify(trackProcessingService, times(1)).processTrack(anyString(), anyLong(), eq(1L), eq(true));
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
