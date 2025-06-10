import com.project.tracking_system.dto.TrackInfoDTO;
import com.project.tracking_system.dto.TrackInfoListDTO;
import com.project.tracking_system.entity.*;
import com.project.tracking_system.repository.*;
import com.project.tracking_system.service.analytics.DeliveryHistoryService;
import com.project.tracking_system.service.track.StatusTrackService;
import com.project.tracking_system.service.track.TypeDefinitionTrackPostService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class DeliveryHistoryTest {

    @Mock
    private StoreAnalyticsRepository storeAnalyticsRepository;
    @Mock
    private TrackParcelRepository trackParcelRepository;
    @Mock
    private DeliveryHistoryRepository deliveryHistoryRepository;
    @Mock
    private PostalServiceStatisticsRepository postalServiceStatisticsRepository;
    @Mock
    private StoreDailyStatisticsRepository storeDailyStatisticsRepository;
    @Mock
    private PostalServiceDailyStatisticsRepository postalServiceDailyStatisticsRepository;
    @Mock
    private TypeDefinitionTrackPostService typeDefinitionTrackPostService;
    @Mock
    private StatusTrackService statusTrackService;

    @InjectMocks
    private DeliveryHistoryService service;

    @Test
    void waitingThenDelivered_AveragePickupDaysCalculated() {
        Store store = new Store();
        store.setId(50L);
        TrackParcel parcel = new TrackParcel();
        parcel.setId(77L);
        parcel.setNumber("PC777");
        parcel.setStore(store);
        parcel.setIncludedInStatistics(false);

        DeliveryHistory history = new DeliveryHistory();
        history.setTrackParcel(parcel);
        history.setStore(store);
        history.setPostalService(PostalServiceType.BELPOST);

        when(deliveryHistoryRepository.findByTrackParcelId(parcel.getId())).thenReturn(Optional.of(history));
        when(typeDefinitionTrackPostService.detectPostalService(parcel.getNumber())).thenReturn(PostalServiceType.BELPOST);

        // STEP 1: статус прибыл и ждёт клиента
        TrackInfoDTO waitDto = new TrackInfoDTO("01.06.2024 10:00:00", "arrived");
        when(statusTrackService.setStatus(List.of(waitDto))).thenReturn(GlobalStatus.WAITING_FOR_CUSTOMER);
        service.updateDeliveryHistory(parcel, GlobalStatus.IN_TRANSIT, GlobalStatus.WAITING_FOR_CUSTOMER,
                new TrackInfoListDTO(List.of(waitDto)));

        ZonedDateTime arrived = ZonedDateTime.of(2024,6,1,10,0,0,0, ZoneId.of("Europe/Minsk"))
                .withZoneSameInstant(ZoneOffset.UTC);
        assertEquals(arrived, history.getArrivedDate());

        // prepare stats for final status
        StoreStatistics storeStats = new StoreStatistics();
        storeStats.setStore(store);
        PostalServiceStatistics psStats = new PostalServiceStatistics();
        psStats.setStore(store);
        psStats.setPostalServiceType(PostalServiceType.BELPOST);
        StoreDailyStatistics daily = new StoreDailyStatistics();
        daily.setStore(store);
        daily.setDate(arrived.plusDays(2).toLocalDate());
        PostalServiceDailyStatistics psDaily = new PostalServiceDailyStatistics();
        psDaily.setStore(store);
        psDaily.setPostalServiceType(PostalServiceType.BELPOST);
        psDaily.setDate(arrived.plusDays(2).toLocalDate());

        when(storeAnalyticsRepository.findByStoreId(store.getId())).thenReturn(Optional.of(storeStats));
        when(postalServiceStatisticsRepository.findByStoreIdAndPostalServiceType(store.getId(), PostalServiceType.BELPOST))
                .thenReturn(Optional.of(psStats));
        when(storeDailyStatisticsRepository.findByStoreIdAndDate(store.getId(), arrived.plusDays(2).toLocalDate()))
                .thenReturn(Optional.of(daily));
        when(postalServiceDailyStatisticsRepository.findByStoreIdAndPostalServiceTypeAndDate(store.getId(),
                PostalServiceType.BELPOST, arrived.plusDays(2).toLocalDate())).thenReturn(Optional.of(psDaily));

        // STEP 2: посылка получена
        TrackInfoDTO deliveredDto = new TrackInfoDTO("03.06.2024 09:00:00", "delivered");
        when(statusTrackService.setStatus(List.of(deliveredDto))).thenReturn(GlobalStatus.DELIVERED);
        service.updateDeliveryHistory(parcel, GlobalStatus.WAITING_FOR_CUSTOMER, GlobalStatus.DELIVERED,
                new TrackInfoListDTO(List.of(deliveredDto)));

        // Check accumulated statistics and averages
        assertEquals(BigDecimal.valueOf(2.0), storeStats.getSumPickupDays());
        assertEquals(BigDecimal.valueOf(2.0), psStats.getSumPickupDays());
        assertEquals(0, storeStats.getAveragePickupDays().compareTo(new BigDecimal("2.00")));
        assertEquals(0, psStats.getAveragePickupDays().compareTo(new BigDecimal("2.00")));
    }
}
