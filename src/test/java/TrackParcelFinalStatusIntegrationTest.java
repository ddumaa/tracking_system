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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.time.ZonedDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class TrackParcelFinalStatusIntegrationTest {

    @Mock
    private WebSocketController webSocketController;
    @Mock
    private TypeDefinitionTrackPostService typeDefinitionTrackPostService;
    @Mock
    private StatusTrackService statusTrackService;
    @Mock
    private SubscriptionService subscriptionService;
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
    @Mock
    private DeliveryHistoryRepository deliveryHistoryRepository;

    private DeliveryHistoryService deliveryHistoryService;
    private TrackParcelService trackParcelService;

    @BeforeEach
    void init() {
        deliveryHistoryService = new DeliveryHistoryService(
                storeAnalyticsRepository,
                deliveryHistoryRepository,
                typeDefinitionTrackPostService,
                statusTrackService,
                trackParcelRepository,
                postalServiceStatisticsRepository,
                storeDailyStatisticsRepository,
                postalServiceDailyStatisticsRepository
        );

        trackParcelService = new TrackParcelService(
                webSocketController,
                typeDefinitionTrackPostService,
                statusTrackService,
                subscriptionService,
                deliveryHistoryService,
                userService,
                userSubscriptionRepository,
                storeRepository,
                userRepository,
                trackParcelRepository,
                storeAnalyticsRepository,
                postalServiceStatisticsRepository,
                storeDailyStatisticsRepository,
                postalServiceDailyStatisticsRepository
        );
    }

    @Test
    void save_FinalDeliveredStatus_UpdatesAnalytics() {
        Long storeId = 1L;
        Long userId = 2L;
        String number = "PC111111111BY";

        TrackInfoDTO delivered = new TrackInfoDTO("03.06.2024 08:00:00", "Почтовое отправление выдано");
        TrackInfoDTO waiting = new TrackInfoDTO("02.06.2024 08:00:00", "Почтовое отправление прибыло на ОПС выдачи");
        TrackInfoDTO sent = new TrackInfoDTO("01.06.2024 08:00:00", "Почтовое отправление принято на ОПС");
        TrackInfoListDTO listDTO = new TrackInfoListDTO(List.of(delivered, waiting, sent));

        Store store = new Store();
        store.setId(storeId);
        User user = new User();
        user.setTimeZone("UTC");

        StoreStatistics storeStats = new StoreStatistics();
        storeStats.setStore(store);
        PostalServiceStatistics psStats = new PostalServiceStatistics();
        psStats.setStore(store);
        psStats.setPostalServiceType(PostalServiceType.BELPOST);
        StoreDailyStatistics daily = new StoreDailyStatistics();
        daily.setStore(store);
        daily.setDate(LocalDate.of(2024,6,3));
        PostalServiceDailyStatistics psDaily = new PostalServiceDailyStatistics();
        psDaily.setStore(store);
        psDaily.setPostalServiceType(PostalServiceType.BELPOST);
        psDaily.setDate(LocalDate.of(2024,6,3));

        when(trackParcelRepository.findByNumberAndUserId(number, userId)).thenReturn(null);
        when(subscriptionService.canSaveMoreTracks(userId, 1)).thenReturn(1);
        when(storeRepository.getReferenceById(storeId)).thenReturn(store);
        when(userRepository.getReferenceById(userId)).thenReturn(user);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(typeDefinitionTrackPostService.detectPostalService(number)).thenReturn(PostalServiceType.BELPOST);
        when(storeAnalyticsRepository.findByStoreId(storeId)).thenReturn(Optional.of(storeStats));
        when(postalServiceStatisticsRepository.findByStoreIdAndPostalServiceType(storeId, PostalServiceType.BELPOST))
                .thenReturn(Optional.of(psStats));
        when(storeDailyStatisticsRepository.findByStoreIdAndDate(storeId, LocalDate.of(2024,6,3)))
                .thenReturn(Optional.of(daily));
        when(postalServiceDailyStatisticsRepository.findByStoreIdAndPostalServiceTypeAndDate(storeId, PostalServiceType.BELPOST, LocalDate.of(2024,6,3)))
                .thenReturn(Optional.of(psDaily));
        when(deliveryHistoryRepository.findByTrackParcelId(any())).thenReturn(Optional.empty());

        final TrackParcel[] saved = new TrackParcel[1];
        when(trackParcelRepository.save(any())).thenAnswer(invocation -> {
            saved[0] = invocation.getArgument(0);
            return saved[0];
        });

        when(statusTrackService.setStatus(any())).thenAnswer(invocation -> {
            List<TrackInfoDTO> list = invocation.getArgument(0);
            String info = list.get(0).getInfoTrack();
            if (info.startsWith("Почтовое отправление выдано")) return GlobalStatus.DELIVERED;
            if (info.startsWith("Почтовое отправление прибыло")) return GlobalStatus.WAITING_FOR_CUSTOMER;
            return GlobalStatus.IN_TRANSIT;
        });

        trackParcelService.save(number, listDTO, storeId, userId);

        assertEquals(GlobalStatus.DELIVERED, saved[0].getStatus());
        assertTrue(saved[0].isIncludedInStatistics());

        assertEquals(1, storeStats.getTotalSent());
        assertEquals(1, storeStats.getTotalDelivered());
        assertEquals(BigDecimal.valueOf(1.0), storeStats.getSumDeliveryDays());
        assertEquals(BigDecimal.valueOf(1.0), storeStats.getSumPickupDays());

        assertEquals(1, psStats.getTotalSent());
        assertEquals(1, psStats.getTotalDelivered());
        assertEquals(BigDecimal.valueOf(1.0), psStats.getSumDeliveryDays());
        assertEquals(BigDecimal.valueOf(1.0), psStats.getSumPickupDays());

        assertEquals(1, daily.getSent());
        assertEquals(1, daily.getDelivered());
        assertEquals(BigDecimal.valueOf(1.0), daily.getSumDeliveryDays());
        assertEquals(BigDecimal.valueOf(1.0), daily.getSumPickupDays());

        assertEquals(1, psDaily.getSent());
        assertEquals(1, psDaily.getDelivered());
        assertEquals(BigDecimal.valueOf(1.0), psDaily.getSumDeliveryDays());
        assertEquals(BigDecimal.valueOf(1.0), psDaily.getSumPickupDays());
    }

    @Test
    void save_FinalStatusWithWaitingHistory_SetsArrivedDateAndMetrics() {
        Long storeId = 10L;
        Long userId = 20L;
        String number = "PC222222222BY";

        TrackInfoDTO delivered = new TrackInfoDTO("03.06.2024 08:00:00", "Почтовое отправление выдано");
        TrackInfoDTO waiting = new TrackInfoDTO("02.06.2024 08:00:00", "Почтовое отправление прибыло на ОПС выдачи");
        TrackInfoDTO shipped = new TrackInfoDTO("01.06.2024 08:00:00", "Почтовое отправление принято на ОПС");
        TrackInfoListDTO listDTO = new TrackInfoListDTO(List.of(delivered, waiting, shipped));

        Store store = new Store();
        store.setId(storeId);
        User user = new User();
        user.setTimeZone("UTC");

        StoreStatistics storeStats = new StoreStatistics();
        storeStats.setStore(store);
        PostalServiceStatistics psStats = new PostalServiceStatistics();
        psStats.setStore(store);
        psStats.setPostalServiceType(PostalServiceType.BELPOST);
        StoreDailyStatistics daily = new StoreDailyStatistics();
        daily.setStore(store);
        daily.setDate(LocalDate.of(2024,6,3));
        PostalServiceDailyStatistics psDaily = new PostalServiceDailyStatistics();
        psDaily.setStore(store);
        psDaily.setPostalServiceType(PostalServiceType.BELPOST);
        psDaily.setDate(LocalDate.of(2024,6,3));

        when(trackParcelRepository.findByNumberAndUserId(number, userId)).thenReturn(null);
        when(subscriptionService.canSaveMoreTracks(userId, 1)).thenReturn(1);
        when(storeRepository.getReferenceById(storeId)).thenReturn(store);
        when(userRepository.getReferenceById(userId)).thenReturn(user);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(typeDefinitionTrackPostService.detectPostalService(number)).thenReturn(PostalServiceType.BELPOST);
        when(storeAnalyticsRepository.findByStoreId(storeId)).thenReturn(Optional.of(storeStats));
        when(postalServiceStatisticsRepository.findByStoreIdAndPostalServiceType(storeId, PostalServiceType.BELPOST))
                .thenReturn(Optional.of(psStats));
        when(storeDailyStatisticsRepository.findByStoreIdAndDate(storeId, LocalDate.of(2024,6,3)))
                .thenReturn(Optional.of(daily));
        when(postalServiceDailyStatisticsRepository.findByStoreIdAndPostalServiceTypeAndDate(storeId, PostalServiceType.BELPOST, LocalDate.of(2024,6,3)))
                .thenReturn(Optional.of(psDaily));
        when(deliveryHistoryRepository.findByTrackParcelId(any())).thenReturn(Optional.empty());

        final TrackParcel[] saved = new TrackParcel[1];
        when(trackParcelRepository.save(any())).thenAnswer(invocation -> {
            saved[0] = invocation.getArgument(0);
            return saved[0];
        });

        ArgumentCaptor<DeliveryHistory> captor = ArgumentCaptor.forClass(DeliveryHistory.class);
        when(deliveryHistoryRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        when(statusTrackService.setStatus(any())).thenAnswer(invocation -> {
            List<TrackInfoDTO> list = invocation.getArgument(0);
            String info = list.get(0).getInfoTrack();
            if (info.startsWith("Почтовое отправление выдано")) return GlobalStatus.DELIVERED;
            if (info.startsWith("Почтовое отправление прибыло")) return GlobalStatus.WAITING_FOR_CUSTOMER;
            return GlobalStatus.IN_TRANSIT;
        });

        trackParcelService.save(number, listDTO, storeId, userId);

        verify(deliveryHistoryRepository).save(captor.capture());
        DeliveryHistory history = captor.getValue();

        ZonedDateTime expectedArrived = ZonedDateTime.of(2024,6,2,8,0,0,0, ZoneId.of("Europe/Minsk"))
                .withZoneSameInstant(ZoneOffset.UTC);
        assertEquals(expectedArrived, history.getArrivedDate());

        assertEquals(GlobalStatus.DELIVERED, saved[0].getStatus());
        assertTrue(saved[0].isIncludedInStatistics());

        assertEquals(1, storeStats.getTotalSent());
        assertEquals(1, storeStats.getTotalDelivered());
        assertEquals(BigDecimal.valueOf(1.0), storeStats.getSumDeliveryDays());
        assertEquals(BigDecimal.valueOf(1.0), storeStats.getSumPickupDays());

        assertEquals(1, psStats.getTotalSent());
        assertEquals(1, psStats.getTotalDelivered());
        assertEquals(BigDecimal.valueOf(1.0), psStats.getSumDeliveryDays());
        assertEquals(BigDecimal.valueOf(1.0), psStats.getSumPickupDays());

        assertEquals(1, daily.getSent());
        assertEquals(1, daily.getDelivered());
        assertEquals(BigDecimal.valueOf(1.0), daily.getSumDeliveryDays());
        assertEquals(BigDecimal.valueOf(1.0), daily.getSumPickupDays());

        assertEquals(1, psDaily.getSent());
        assertEquals(1, psDaily.getDelivered());
        assertEquals(BigDecimal.valueOf(1.0), psDaily.getSumDeliveryDays());
        assertEquals(BigDecimal.valueOf(1.0), psDaily.getSumPickupDays());
    }
}
