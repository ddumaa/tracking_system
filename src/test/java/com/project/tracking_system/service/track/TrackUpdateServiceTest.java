package com.project.tracking_system.service.track;

import com.project.tracking_system.controller.WebSocketController;
import com.project.tracking_system.dto.TrackProcessingProgressDTO;
import com.project.tracking_system.dto.TrackingResultAdd;
import com.project.tracking_system.entity.PostalServiceType;
import com.project.tracking_system.service.belpost.QueuedTrack;
import com.project.tracking_system.service.track.TrackSource;
import com.project.tracking_system.repository.StoreRepository;
import com.project.tracking_system.repository.TrackParcelRepository;
import com.project.tracking_system.service.SubscriptionService;
import com.project.tracking_system.service.admin.ApplicationSettingsService;
import com.project.tracking_system.service.belpost.BelPostTrackQueueService;
import com.project.tracking_system.service.user.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Тесты для {@link TrackUpdateService}, проверяющие координационную логику.
 * <p>
 * Сервис группирует треки по почтовым службам и делегирует их
 * соответствующим обработчикам. В тестах мы убеждаемся, что
 * группировка и делегирование выполняются корректно без обращения
 * к реальной инфраструктуре.
 * </p>
 */
@ExtendWith(MockitoExtension.class)
class TrackUpdateServiceTest {

    // region зависимости, необходимые сервису
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
    // endregion

    /**
     * Тестируемый сервис.
     */
    private TrackUpdateService service;

    @BeforeEach
    void setUp() {
        // Все зависимости мокируются, чтобы сосредоточиться на логике
        // группировки и делегирования.
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
     * Проверяет, что сервис группирует входящие треки и делегирует
     * их диспетчеру. Здесь используются только треки, не относящиеся
     * к Белпочте, чтобы упростить сценарий.
     */
    @Test
    void process_GroupsAndDelegates() {
        // Подготовка
        TrackMeta meta = new TrackMeta("A1", 1L, null, true, PostalServiceType.EVROPOST);
        Map<PostalServiceType, List<TrackMeta>> grouped = new HashMap<>();
        grouped.put(PostalServiceType.EVROPOST, List.of(meta));
        when(groupingService.group(List.of(meta))).thenReturn(grouped);
        List<TrackingResultAdd> expected = List.of(new TrackingResultAdd("A1", "ok"));
        when(dispatcherService.dispatch(grouped, 5L)).thenReturn(expected);
        when(progressAggregatorService.getProgress(anyLong()))
                .thenReturn(new TrackProcessingProgressDTO(1L, 1, 1, "0:00"));

        // Действие
        List<TrackingResultAdd> actual = service.process(List.of(meta), 5L);

        // Проверка
        assertEquals(expected, actual);
        verify(progressAggregatorService).registerBatch(anyLong(), eq(1), eq(5L));
        verify(progressAggregatorService).trackProcessed(anyLong());
        verify(trackingResultCacheService).addResult(eq(5L), any());
        verify(belPostTrackQueueService, never()).enqueue(anyList());
    }

    /**
     * Убеждаемся, что треки Белпочты отправляются в очередь,
     * а остальные делегируются диспетчеру.
     */
    @Test
    void process_EnqueuesBelpostTracks() {
        // Подготовка
        TrackMeta bel = new TrackMeta("B1", 2L, null, true, PostalServiceType.BELPOST);
        TrackMeta evro = new TrackMeta("E1", 3L, null, true, PostalServiceType.EVROPOST);
        Map<PostalServiceType, List<TrackMeta>> grouped = new HashMap<>();
        grouped.put(PostalServiceType.BELPOST, List.of(bel));
        grouped.put(PostalServiceType.EVROPOST, List.of(evro));
        when(groupingService.group(List.of(bel, evro))).thenReturn(grouped);
        when(dispatcherService.dispatch(anyMap(), eq(5L)))
                .thenReturn(List.of(new TrackingResultAdd("E1", "ok")));
        when(progressAggregatorService.getProgress(anyLong()))
                .thenReturn(new TrackProcessingProgressDTO(1L, 1, 1, "0:00"));

        // Действие
        service.process(List.of(bel, evro), 5L);

        // Проверка
        ArgumentCaptor<Map<PostalServiceType, List<TrackMeta>>> mapCaptor = ArgumentCaptor.forClass(Map.class);
        verify(dispatcherService).dispatch(mapCaptor.capture(), eq(5L));
        assertEquals(1, mapCaptor.getValue().size());
        assertEquals(List.of(evro), mapCaptor.getValue().get(PostalServiceType.EVROPOST));

        ArgumentCaptor<List<QueuedTrack>> queueCaptor = ArgumentCaptor.forClass(List.class);
        verify(belPostTrackQueueService).enqueue(queueCaptor.capture());
        assertEquals(1, queueCaptor.getValue().size());
        QueuedTrack qt = queueCaptor.getValue().get(0);
        assertEquals("B1", qt.trackNumber());
        assertEquals(5L, qt.userId());
        assertEquals(2L, qt.storeId());
        assertEquals(TrackSource.UPDATE, qt.source());
        verify(progressAggregatorService, atLeastOnce()).trackProcessed(anyLong());
        verify(trackingResultCacheService, atLeastOnce()).addResult(eq(5L), any());
    }
}

