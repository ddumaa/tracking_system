package com.project.tracking_system.service.track;

import com.project.tracking_system.entity.GlobalStatus;
import com.project.tracking_system.entity.Store;
import com.project.tracking_system.entity.TrackParcel;
import com.project.tracking_system.model.subscription.FeatureKey;
import com.project.tracking_system.repository.TrackParcelRepository;
import com.project.tracking_system.repository.UserSubscriptionRepository;
import com.project.tracking_system.service.SubscriptionService;
import com.project.tracking_system.service.user.UserService;
import com.project.tracking_system.dto.TrackingResultAdd;
import com.project.tracking_system.service.track.TrackUpdateCoordinatorService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyList;
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
    private TrackUpdateCoordinatorService coordinatorService;
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
                coordinatorService,
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

        when(coordinatorService.process(anyList(), eq(1L)))
                .thenReturn(List.of(new TrackingResultAdd("A1", "ok")));

        scheduler.updateAllUsersTracks();

        verify(trackParcelRepository).findByUserId(1L);
        verify(trackParcelRepository, never()).findByUserId(2L);
        verify(coordinatorService).process(anyList(), eq(1L));
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

        when(coordinatorService.process(anyList(), eq(1L)))
                .thenReturn(List.of(new TrackingResultAdd("A1", "ok")));

        scheduler.updateAllUsersTracks();

        verify(coordinatorService).process(anyList(), eq(1L));
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

        verify(coordinatorService, never()).process(any(), anyLong());
    }

    /**
     * Убеждаемся, что ошибка обновления одного пользователя не влияет на других.
     */
    @Test
    void updateAllUsersTracks_ContinueOnUserError() {
        when(userSubscriptionRepository.findUserIdsByFeature(FeatureKey.AUTO_UPDATE))
                .thenReturn(List.of(1L, 2L));
        when(userService.isAutoUpdateEnabled(anyLong())).thenReturn(true);

        TrackParcel parcel1 = buildParcel("A1", GlobalStatus.IN_TRANSIT, 10L);
        TrackParcel parcel2 = buildParcel("A2", GlobalStatus.IN_TRANSIT, 11L);
        when(trackParcelRepository.findByUserId(1L)).thenReturn(List.of(parcel1));
        when(trackParcelRepository.findByUserId(2L)).thenReturn(List.of(parcel2));

        when(subscriptionService.canUpdateTracks(anyLong(), eq(1))).thenReturn(1);

        when(coordinatorService.process(anyList(), eq(1L)))
                .thenThrow(new RuntimeException("fail"));
        when(coordinatorService.process(anyList(), eq(2L)))
                .thenReturn(List.of(new TrackingResultAdd("A2", "ok")));

        scheduler.updateAllUsersTracks();

        verify(coordinatorService).process(anyList(), eq(1L));
        verify(coordinatorService).process(anyList(), eq(2L));
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
