package com.project.tracking_system.service.track;

import com.project.tracking_system.entity.GlobalStatus;
import com.project.tracking_system.entity.Store;
import com.project.tracking_system.entity.TrackParcel;
import com.project.tracking_system.model.subscription.FeatureKey;
import com.project.tracking_system.repository.TrackParcelRepository;
import com.project.tracking_system.repository.UserSubscriptionRepository;
import com.project.tracking_system.service.SubscriptionService;
import com.project.tracking_system.service.user.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/**
 * Тесты для {@link TrackAutoUpdateScheduler}.
 */
@ExtendWith(MockitoExtension.class)
class TrackAutoUpdateSchedulerTest {

    @Mock
    private UserSubscriptionRepository userSubscriptionRepository;
    @Mock
    private TrackParcelRepository trackParcelRepository;
    @Mock
    private TrackProcessingService trackProcessingService;
    @Mock
    private SubscriptionService subscriptionService;
    @Mock
    private UserService userService;

    private TrackAutoUpdateScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new TrackAutoUpdateScheduler(
                userSubscriptionRepository,
                trackParcelRepository,
                trackProcessingService,
                subscriptionService,
                userService
        );
    }

    /**
     * Проверяем, что обновление выполняется только для пользователей
     * с включённым автообновлением.
     */
    @Test
    void updateAllUsersTracks_RunsOnlyForAutoUpdateEnabledUsers() {
        // userId=1 разрешено, userId=2 запрещено
        when(userSubscriptionRepository.findUserIdsByFeature(FeatureKey.AUTO_UPDATE))
                .thenReturn(List.of(1L, 2L));
        when(userService.isAutoUpdateEnabled(1L)).thenReturn(true);
        when(userService.isAutoUpdateEnabled(2L)).thenReturn(false);

        TrackParcel parcel = buildParcel("A1", GlobalStatus.IN_TRANSIT, 10L);
        when(trackParcelRepository.findByUserId(1L)).thenReturn(List.of(parcel));
        when(subscriptionService.canUpdateTracks(1L, 1)).thenReturn(1);

        scheduler.updateAllUsersTracks();

        verify(trackParcelRepository).findByUserId(1L);
        verify(trackParcelRepository, never()).findByUserId(2L);
        verify(trackProcessingService).processTrack("A1", 10L, 1L, true);
    }

    /**
     * Проверяем, что треки в финальном статусе пропускаются.
     */
    @Test
    void updateAllUsersTracks_SkipsFinalStatusTracks() {
        when(userSubscriptionRepository.findUserIdsByFeature(FeatureKey.AUTO_UPDATE))
                .thenReturn(List.of(1L));
        when(userService.isAutoUpdateEnabled(1L)).thenReturn(true);

        TrackParcel finished = buildParcel("F1", GlobalStatus.DELIVERED, 10L);
        TrackParcel active = buildParcel("A1", GlobalStatus.IN_TRANSIT, 10L);
        when(trackParcelRepository.findByUserId(1L)).thenReturn(List.of(finished, active));
        when(subscriptionService.canUpdateTracks(1L, 1)).thenReturn(1);

        scheduler.updateAllUsersTracks();

        verify(trackProcessingService).processTrack("A1", 10L, 1L, true);
        verify(trackProcessingService, never()).processTrack("F1", 10L, 1L, true);
    }

    /**
     * Проверяем, что при превышении лимита обновление не выполняется.
     */
    @Test
    void updateAllUsersTracks_NoUpdateWhenLimitExceeded() {
        when(userSubscriptionRepository.findUserIdsByFeature(FeatureKey.AUTO_UPDATE))
                .thenReturn(List.of(1L));
        when(userService.isAutoUpdateEnabled(1L)).thenReturn(true);

        TrackParcel parcel = buildParcel("A1", GlobalStatus.IN_TRANSIT, 10L);
        when(trackParcelRepository.findByUserId(1L)).thenReturn(List.of(parcel));
        when(subscriptionService.canUpdateTracks(1L, 1)).thenReturn(0);

        scheduler.updateAllUsersTracks();

        verify(trackProcessingService, never()).processTrack(any(), anyLong(), anyLong(), anyBoolean());
    }

    /**
     * Создаёт посылку с заданными параметрами.
     */
    private TrackParcel buildParcel(String number, GlobalStatus status, Long storeId) {
        Store store = new Store();
        store.setId(storeId);
        TrackParcel parcel = new TrackParcel();
        parcel.setNumber(number);
        parcel.setStatus(status);
        parcel.setStore(store);
        return parcel;
    }
}
