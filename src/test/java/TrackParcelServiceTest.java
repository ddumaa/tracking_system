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
}
