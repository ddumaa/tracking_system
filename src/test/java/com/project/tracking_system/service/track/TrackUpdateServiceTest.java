package com.project.tracking_system.service.track;

import com.project.tracking_system.controller.WebSocketController;
import com.project.tracking_system.entity.GlobalStatus;
import com.project.tracking_system.entity.TrackParcel;
import com.project.tracking_system.entity.UpdateResult;
import com.project.tracking_system.repository.StoreRepository;
import com.project.tracking_system.repository.TrackParcelRepository;
import com.project.tracking_system.service.SubscriptionService;
import com.project.tracking_system.model.subscription.FeatureKey;
import com.project.tracking_system.service.admin.ApplicationSettingsService;
import com.project.tracking_system.service.belpost.BelPostTrackQueueService;
import com.project.tracking_system.service.user.UserService;
import com.project.tracking_system.service.track.TrackParcelService;
import com.project.tracking_system.service.track.TrackUploadGroupingService;
import com.project.tracking_system.service.track.TrackUpdateDispatcherService;
import com.project.tracking_system.service.track.ProgressAggregatorService;
import com.project.tracking_system.service.track.TrackingResultCacheService;
import com.project.tracking_system.service.track.TrackMeta;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

import com.project.tracking_system.entity.PostalServiceType;
import com.project.tracking_system.service.belpost.QueuedTrack;
import com.project.tracking_system.service.track.TrackSource;
import org.mockito.ArgumentCaptor;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Тесты для {@link TrackUpdateService}.
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
                dispatcherService,
                belPostTrackQueueService,
                progressAggregatorService,
                trackingResultCacheService,
                applicationSettingsService,
                userService
        );
    }

    /**
     * Проверяем, что посылки с недавним обновлением пропускаются.
     */
    @Test
    void updateAllParcels_SkipsRecentUpdates() {
        when(subscriptionService.isFeatureEnabled(1L, FeatureKey.BULK_UPDATE)).thenReturn(true);
        when(storeRepository.countByOwnerId(1L)).thenReturn(1);
        when(applicationSettingsService.getTrackUpdateIntervalHours()).thenReturn(3);

        TrackParcel parcel = new TrackParcel();
        parcel.setStatus(GlobalStatus.IN_TRANSIT);
        parcel.setLastUpdate(ZonedDateTime.now(ZoneOffset.UTC).minusHours(1));
        when(trackParcelRepository.findByUserId(1L)).thenReturn(List.of(parcel));

        TrackUpdateService spy = spy(service);
        UpdateResult result = spy.updateAllParcels(1L);

        assertEquals(0, result.getUpdateCount());
        verify(spy, never()).processAllTrackUpdatesAsync(anyLong(), anyList());
    }

    /**
     * Проверяем, что при выборе одного трека с недавним обновлением
     * возвращается сообщение о времени следующего обновления.
     */
    @Test
    void updateSelectedParcels_ReturnsNextAllowedTime() {
        when(applicationSettingsService.getTrackUpdateIntervalHours()).thenReturn(3);

        TrackParcel parcel = new TrackParcel();
        parcel.setNumber("A1");
        parcel.setStatus(GlobalStatus.IN_TRANSIT);
        parcel.setLastUpdate(ZonedDateTime.now(ZoneOffset.UTC).minusHours(1));
        when(trackParcelRepository.findByNumberInAndUserId(List.of("A1"), 5L))
                .thenReturn(List.of(parcel));
        when(userService.getUserZone(5L)).thenReturn(ZoneOffset.UTC);

        TrackUpdateService spy = spy(service);
        UpdateResult result = spy.updateSelectedParcels(5L, List.of("A1"));

        assertEquals(0, result.getUpdateCount());
        verify(spy, never()).processTrackUpdatesAsync(anyLong(), anyList(), anyInt(), anyInt());
        verify(webSocketController).sendUpdateStatus(eq(5L), contains("следующее обновление"), eq(false));
    }

    /**
     * Убеждаемся, что при обработке треков Белпочты
     * они ставятся в очередь с источником UPDATE.
     */
    @Test
    void process_EnqueuesBelpostTracksWithUpdateSource() {
        TrackMeta meta = new TrackMeta("B1", 1L, null, true, PostalServiceType.BELPOST);
        when(groupingService.group(anyList()))
                .thenReturn(Map.of(PostalServiceType.BELPOST, List.of(meta)));
        when(dispatcherService.dispatch(anyMap(), anyLong())).thenReturn(List.of());

        service.process(List.of(meta), 3L);

        ArgumentCaptor<List<QueuedTrack>> captor = ArgumentCaptor.forClass(List.class);
        verify(belPostTrackQueueService).enqueue(captor.capture());
        assertEquals(TrackSource.UPDATE, captor.getValue().get(0).source());
    }
}
