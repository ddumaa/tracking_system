package com.project.tracking_system.service.track;

import com.project.tracking_system.controller.WebSocketController;
import com.project.tracking_system.dto.TrackProcessingProgressDTO;
import com.project.tracking_system.dto.TrackingResultAdd;
import com.project.tracking_system.entity.PostalServiceType;
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
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests for {@link TrackUpdateService} that verify coordination logic.
 * <p>
 * The service groups tracks by postal service and delegates them
 * to specific processors. Here we check that the grouping and
 * delegation happen correctly without touching real infrastructure
 * components.
 * </p>
 */
@ExtendWith(MockitoExtension.class)
class TrackUpdateServiceTest {

    // region collaborators required by the service
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
     * Service under test.
     */
    private TrackUpdateService service;

    @BeforeEach
    void setUp() {
        // Each dependency is mocked so the test focuses solely on
        // coordination of grouping and dispatching.
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
     * Ensures that the service groups incoming tracks and delegates
     * them to the dispatcher. Only non-BELPOST tracks are used here
     * to simplify the scenario.
     */
    @Test
    void process_GroupsAndDelegates() {
        // Arrange
        TrackMeta meta = new TrackMeta("A1", 1L, null, true, PostalServiceType.EVROPOST);
        Map<PostalServiceType, List<TrackMeta>> grouped = Map.of(PostalServiceType.EVROPOST, List.of(meta));
        when(groupingService.group(List.of(meta))).thenReturn(grouped);
        List<TrackingResultAdd> expected = List.of(new TrackingResultAdd("A1", "ok"));
        when(dispatcherService.dispatch(grouped, 5L)).thenReturn(expected);
        when(progressAggregatorService.getProgress(anyLong()))
                .thenReturn(new TrackProcessingProgressDTO(1L, 1, 1, "0:00"));

        // Act
        List<TrackingResultAdd> actual = service.process(List.of(meta), 5L);

        // Assert
        assertEquals(expected, actual);
        verify(progressAggregatorService).registerBatch(anyLong(), eq(1), eq(5L));
        verify(progressAggregatorService).trackProcessed(anyLong());
        verify(trackingResultCacheService).addResult(eq(5L), any());
        verify(belPostTrackQueueService, never()).enqueue(anyList());
    }
}

