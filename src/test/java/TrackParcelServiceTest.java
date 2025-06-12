import com.project.tracking_system.controller.WebSocketController;
import com.project.tracking_system.dto.TrackInfoDTO;
import com.project.tracking_system.dto.TrackInfoListDTO;
import com.project.tracking_system.entity.*;
import com.project.tracking_system.repository.*;
import com.project.tracking_system.service.SubscriptionService;
import com.project.tracking_system.service.analytics.DeliveryHistoryService;
import com.project.tracking_system.service.track.StatusTrackService;
import com.project.tracking_system.service.track.TrackParcelService;
import com.project.tracking_system.service.track.TypeDefinitionTrackPostService;
import com.project.tracking_system.service.user.UserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.*;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class TrackParcelServiceTest {

    @Mock
    private WebSocketController webSocketController;
    @Mock
    private TypeDefinitionTrackPostService typeDefinitionTrackPostService;
    @Mock
    private StatusTrackService statusTrackService;
    @Mock
    private SubscriptionService subscriptionService;
    @Mock
    private DeliveryHistoryService deliveryHistoryService;
    @Mock
    private UserService userService;
    @Mock
    private UserSubscriptionRepository userSubscriptionRepository;
    @Mock
    private StoreRepository storeRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private TrackParcelRepository trackParcelRepository;
    @Mock
    private StoreAnalyticsRepository storeAnalyticsRepository;
    @Mock
    private PostalServiceStatisticsRepository postalServiceStatisticsRepository;
    @Mock
    private StoreDailyStatisticsRepository storeDailyStatisticsRepository;
    @Mock
    private PostalServiceDailyStatisticsRepository postalServiceDailyStatisticsRepository;

    @InjectMocks
    private TrackParcelService trackParcelService;

    @Test
    void save_NewTrack_CreatesDailyStats() {
        Long storeId = 1L;
        Long userId = 2L;
        String number = "PC123456789BY";
        ZoneId zone = ZoneOffset.UTC;

        // track info with single status
        TrackInfoDTO info = new TrackInfoDTO("01.01.2024 10:00:00", "info");
        TrackInfoListDTO listDTO = new TrackInfoListDTO(List.of(info));

        Store store = new Store();
        store.setId(storeId);
        User user = new User();
        user.setTimeZone("UTC");
        StoreStatistics storeStats = new StoreStatistics();
        storeStats.setStore(store);
        PostalServiceStatistics psStats = new PostalServiceStatistics();
        psStats.setStore(store);
        psStats.setPostalServiceType(PostalServiceType.BELPOST);

        when(trackParcelRepository.findByNumberAndUserId(number, userId)).thenReturn(null);
        when(subscriptionService.canSaveMoreTracks(userId, 1)).thenReturn(1);
        when(storeRepository.getReferenceById(storeId)).thenReturn(store);
        when(userRepository.getReferenceById(userId)).thenReturn(user);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(statusTrackService.setStatus(any())).thenReturn(GlobalStatus.IN_TRANSIT);
        when(storeAnalyticsRepository.findByStoreId(storeId)).thenReturn(Optional.of(storeStats));
        when(typeDefinitionTrackPostService.detectPostalService(number)).thenReturn(PostalServiceType.BELPOST);
        when(postalServiceStatisticsRepository.findByStoreIdAndPostalServiceType(storeId, PostalServiceType.BELPOST))
                .thenReturn(Optional.empty());
        when(storeDailyStatisticsRepository.findByStoreIdAndDate(eq(storeId), any()))
                .thenReturn(Optional.empty());
        when(postalServiceDailyStatisticsRepository.findByStoreIdAndPostalServiceTypeAndDate(eq(storeId), eq(PostalServiceType.BELPOST), any()))
                .thenReturn(Optional.empty());
        ArgumentCaptor<StoreDailyStatistics> dailyCaptor = ArgumentCaptor.forClass(StoreDailyStatistics.class);
        ArgumentCaptor<PostalServiceDailyStatistics> psDailyCaptor = ArgumentCaptor.forClass(PostalServiceDailyStatistics.class);

        trackParcelService.save(number, listDTO, storeId, userId);

        assertEquals(1, storeStats.getTotalSent());
        verify(storeDailyStatisticsRepository).save(dailyCaptor.capture());
        verify(postalServiceDailyStatisticsRepository).save(psDailyCaptor.capture());
        verify(postalServiceStatisticsRepository).save(any());

        StoreDailyStatistics dailySaved = dailyCaptor.getValue();
        assertEquals(1, dailySaved.getSent());
        assertEquals(LocalDate.of(2024,1,1), dailySaved.getDate());

        PostalServiceDailyStatistics psDailySaved = psDailyCaptor.getValue();
        assertEquals(1, psDailySaved.getSent());
        assertEquals(LocalDate.of(2024,1,1), psDailySaved.getDate());
    }

    @Test
    void save_ExistingTrack_StoreChanged_UpdatesStats() {
        Long oldStoreId = 1L;
        Long newStoreId = 2L;
        Long userId = 3L;
        String number = "PC987654321BY";

        TrackInfoDTO info = new TrackInfoDTO("02.01.2024 12:00:00", "info");
        TrackInfoListDTO listDTO = new TrackInfoListDTO(List.of(info));

        Store oldStore = new Store();
        oldStore.setId(oldStoreId);
        Store newStore = new Store();
        newStore.setId(newStoreId);

        User user = new User();
        user.setTimeZone("UTC");

        TrackParcel existing = new TrackParcel();
        existing.setNumber(number);
        existing.setStore(oldStore);
        existing.setUser(user);
        existing.setData(ZonedDateTime.of(2024, 1, 1, 9, 0, 0, 0, ZoneOffset.UTC));
        existing.setStatus(GlobalStatus.IN_TRANSIT);

        StoreStatistics oldStats = new StoreStatistics();
        oldStats.setStore(oldStore);
        oldStats.setTotalSent(5);
        StoreStatistics newStats = new StoreStatistics();
        newStats.setStore(newStore);

        PostalServiceStatistics oldPsStats = new PostalServiceStatistics();
        oldPsStats.setStore(oldStore);
        oldPsStats.setPostalServiceType(PostalServiceType.BELPOST);
        oldPsStats.setTotalSent(5);
        PostalServiceStatistics newPsStats = new PostalServiceStatistics();
        newPsStats.setStore(newStore);
        newPsStats.setPostalServiceType(PostalServiceType.BELPOST);

        StoreDailyStatistics oldDaily = new StoreDailyStatistics();
        oldDaily.setStore(oldStore);
        oldDaily.setDate(existing.getData().toLocalDate());
        oldDaily.setSent(5);
        StoreDailyStatistics newDaily = new StoreDailyStatistics();
        newDaily.setStore(newStore);
        newDaily.setDate(LocalDate.of(2024, 1, 2));

        PostalServiceDailyStatistics oldPsDaily = new PostalServiceDailyStatistics();
        oldPsDaily.setStore(oldStore);
        oldPsDaily.setPostalServiceType(PostalServiceType.BELPOST);
        oldPsDaily.setDate(existing.getData().toLocalDate());
        oldPsDaily.setSent(5);
        PostalServiceDailyStatistics newPsDaily = new PostalServiceDailyStatistics();
        newPsDaily.setStore(newStore);
        newPsDaily.setPostalServiceType(PostalServiceType.BELPOST);
        newPsDaily.setDate(LocalDate.of(2024, 1, 2));

        when(trackParcelRepository.findByNumberAndUserId(number, userId)).thenReturn(existing);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(statusTrackService.setStatus(any())).thenReturn(GlobalStatus.IN_TRANSIT);
        when(storeRepository.getReferenceById(newStoreId)).thenReturn(newStore);
        when(typeDefinitionTrackPostService.detectPostalService(number)).thenReturn(PostalServiceType.BELPOST);
        when(storeAnalyticsRepository.findByStoreId(oldStoreId)).thenReturn(Optional.of(oldStats));
        when(storeAnalyticsRepository.findByStoreId(newStoreId)).thenReturn(Optional.of(newStats));
        when(postalServiceStatisticsRepository.findByStoreIdAndPostalServiceType(oldStoreId, PostalServiceType.BELPOST))
                .thenReturn(Optional.of(oldPsStats));
        when(postalServiceStatisticsRepository.findByStoreIdAndPostalServiceType(newStoreId, PostalServiceType.BELPOST))
                .thenReturn(Optional.of(newPsStats));
        when(storeDailyStatisticsRepository.findByStoreIdAndDate(oldStoreId, existing.getData().toLocalDate()))
                .thenReturn(Optional.of(oldDaily));
        when(storeDailyStatisticsRepository.findByStoreIdAndDate(eq(newStoreId), any()))
                .thenReturn(Optional.of(newDaily));
        when(postalServiceDailyStatisticsRepository.findByStoreIdAndPostalServiceTypeAndDate(oldStoreId, PostalServiceType.BELPOST, existing.getData().toLocalDate()))
                .thenReturn(Optional.of(oldPsDaily));
        when(postalServiceDailyStatisticsRepository.findByStoreIdAndPostalServiceTypeAndDate(eq(newStoreId), eq(PostalServiceType.BELPOST), any()))
                .thenReturn(Optional.of(newPsDaily));

        trackParcelService.save(number, listDTO, newStoreId, userId);

        assertEquals(newStore, existing.getStore());
        assertEquals(4, oldStats.getTotalSent());
        assertEquals(1, newStats.getTotalSent());
        assertEquals(4, oldDaily.getSent());
        assertEquals(1, newDaily.getSent());
        assertEquals(4, oldPsStats.getTotalSent());
        assertEquals(1, newPsStats.getTotalSent());
        assertEquals(4, oldPsDaily.getSent());
        assertEquals(1, newPsDaily.getSent());

        verify(storeAnalyticsRepository).save(oldStats);
        verify(storeAnalyticsRepository).save(newStats);
        verify(postalServiceStatisticsRepository).save(oldPsStats);
        verify(postalServiceStatisticsRepository).save(newPsStats);
        verify(storeDailyStatisticsRepository).save(oldDaily);
        verify(storeDailyStatisticsRepository).save(newDaily);
        verify(postalServiceDailyStatisticsRepository).save(oldPsDaily);
        verify(postalServiceDailyStatisticsRepository).save(newPsDaily);
    }

    @Test
    void isNewTrack_NoExisting_ReturnsTrue() {
        String number = "RR123";
        Long storeId = 10L;

        when(trackParcelRepository.findByNumberAndStoreId(number, storeId)).thenReturn(null);

        assertTrue(trackParcelService.isNewTrack(number, storeId));
    }

    @Test
    void isNewTrack_Existing_ReturnsFalse() {
        String number = "RR124";
        Long storeId = 11L;
        TrackParcel parcel = new TrackParcel();

        when(trackParcelRepository.findByNumberAndStoreId(number, storeId)).thenReturn(parcel);

        assertFalse(trackParcelService.isNewTrack(number, storeId));
    }

    @Test
    void isNewTrack_NullStoreId_ReturnsTrue() {
        String number = "RR125";

        assertTrue(trackParcelService.isNewTrack(number, null));
    }
}
