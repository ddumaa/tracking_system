import com.project.tracking_system.entity.*;
import com.project.tracking_system.repository.PostalServiceStatisticsRepository;
import com.project.tracking_system.repository.StoreAnalyticsRepository;
import com.project.tracking_system.repository.TrackParcelRepository;
import com.project.tracking_system.repository.StoreDailyStatisticsRepository;
import com.project.tracking_system.repository.PostalServiceDailyStatisticsRepository;
import com.project.tracking_system.service.analytics.DeliveryHistoryService;
import com.project.tracking_system.service.track.TypeDefinitionTrackPostService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class DeliveryHistoryServiceTest {

    @Mock
    private StoreAnalyticsRepository storeAnalyticsRepository;
    @Mock
    private TrackParcelRepository trackParcelRepository;
    @Mock
    private PostalServiceStatisticsRepository postalServiceStatisticsRepository;
    @Mock
    private StoreDailyStatisticsRepository storeDailyStatisticsRepository;
    @Mock
    private PostalServiceDailyStatisticsRepository postalServiceDailyStatisticsRepository;
    @Mock
    private TypeDefinitionTrackPostService typeDefinitionTrackPostService;

    @InjectMocks
    private DeliveryHistoryService deliveryHistoryService;

    @Test
    void registerFinalStatus_IncrementsDeliveredStats() {
        Store store = new Store();
        store.setId(1L);
        TrackParcel parcel = new TrackParcel();
        parcel.setStore(store);
        parcel.setIncludedInStatistics(false);
        DeliveryHistory history = new DeliveryHistory();
        history.setTrackParcel(parcel);
        history.setStore(store);
        history.setPostalService(PostalServiceType.BELPOST);
        ZonedDateTime send = ZonedDateTime.of(2024, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC);
        ZonedDateTime arrived = send.plusDays(1);
        ZonedDateTime received = send.plusDays(2);
        history.setSendDate(send);
        history.setArrivedDate(arrived);
        history.setReceivedDate(received);

        StoreStatistics storeStats = new StoreStatistics();
        PostalServiceStatistics psStats = new PostalServiceStatistics();
        psStats.setStore(store);
        psStats.setPostalServiceType(PostalServiceType.BELPOST);
        StoreDailyStatistics dailyStats = new StoreDailyStatistics();
        dailyStats.setStore(store);
        dailyStats.setDate(received.toLocalDate());
        PostalServiceDailyStatistics psDaily = new PostalServiceDailyStatistics();
        psDaily.setStore(store);
        psDaily.setPostalServiceType(PostalServiceType.BELPOST);
        psDaily.setDate(received.toLocalDate());

        when(storeAnalyticsRepository.findByStoreId(store.getId())).thenReturn(Optional.of(storeStats));
        when(postalServiceStatisticsRepository.findByStoreIdAndPostalServiceType(store.getId(), PostalServiceType.BELPOST))
                .thenReturn(Optional.of(psStats));
        when(storeDailyStatisticsRepository.findByStoreIdAndDate(store.getId(), received.toLocalDate())).thenReturn(Optional.of(dailyStats));
        when(postalServiceDailyStatisticsRepository.findByStoreIdAndPostalServiceTypeAndDate(store.getId(), PostalServiceType.BELPOST, received.toLocalDate()))
                .thenReturn(Optional.of(psDaily));

        deliveryHistoryService.registerFinalStatus(history, GlobalStatus.DELIVERED);

        assertTrue(parcel.isIncludedInStatistics());
        assertEquals(1, storeStats.getTotalDelivered());
        assertEquals(BigDecimal.valueOf(2.0), storeStats.getSumDeliveryDays());
        assertEquals(BigDecimal.valueOf(1.0), storeStats.getSumPickupDays());
        assertEquals(1, psStats.getTotalDelivered());
        assertEquals(BigDecimal.valueOf(2.0), psStats.getSumDeliveryDays());
        assertEquals(BigDecimal.valueOf(1.0), psStats.getSumPickupDays());
        assertEquals(1, dailyStats.getDelivered());
        assertEquals(BigDecimal.valueOf(2.0), dailyStats.getSumDeliveryDays());
        assertEquals(BigDecimal.valueOf(1.0), dailyStats.getSumPickupDays());
        assertEquals(1, psDaily.getDelivered());
        assertEquals(BigDecimal.valueOf(2.0), psDaily.getSumDeliveryDays());
        assertEquals(BigDecimal.valueOf(1.0), psDaily.getSumPickupDays());
        verify(storeAnalyticsRepository).save(storeStats);
        verify(postalServiceStatisticsRepository).save(psStats);
        verify(storeDailyStatisticsRepository).save(dailyStats);
        verify(postalServiceDailyStatisticsRepository).save(psDaily);
        verify(trackParcelRepository).save(parcel);
    }

    @Test
    void registerFinalStatus_IncrementsReturnedStats() {
        Store store = new Store();
        store.setId(2L);
        TrackParcel parcel = new TrackParcel();
        parcel.setStore(store);
        parcel.setIncludedInStatistics(false);
        DeliveryHistory history = new DeliveryHistory();
        history.setTrackParcel(parcel);
        history.setStore(store);
        history.setPostalService(PostalServiceType.BELPOST);
        ZonedDateTime arrived = ZonedDateTime.of(2024, 1, 2, 0, 0, 0, 0, ZoneOffset.UTC);
        ZonedDateTime returned = arrived.plusDays(1);
        history.setArrivedDate(arrived);
        history.setReturnedDate(returned);

        StoreStatistics storeStats = new StoreStatistics();
        PostalServiceStatistics psStats = new PostalServiceStatistics();
        psStats.setStore(store);
        psStats.setPostalServiceType(PostalServiceType.BELPOST);
        StoreDailyStatistics dailyStats = new StoreDailyStatistics();
        dailyStats.setStore(store);
        dailyStats.setDate(returned.toLocalDate());
        PostalServiceDailyStatistics psDaily = new PostalServiceDailyStatistics();
        psDaily.setStore(store);
        psDaily.setPostalServiceType(PostalServiceType.BELPOST);
        psDaily.setDate(returned.toLocalDate());

        when(storeAnalyticsRepository.findByStoreId(store.getId())).thenReturn(Optional.of(storeStats));
        when(postalServiceStatisticsRepository.findByStoreIdAndPostalServiceType(store.getId(), PostalServiceType.BELPOST))
                .thenReturn(Optional.of(psStats));
        when(storeDailyStatisticsRepository.findByStoreIdAndDate(store.getId(), returned.toLocalDate())).thenReturn(Optional.of(dailyStats));
        when(postalServiceDailyStatisticsRepository.findByStoreIdAndPostalServiceTypeAndDate(store.getId(), PostalServiceType.BELPOST, returned.toLocalDate()))
                .thenReturn(Optional.of(psDaily));

        deliveryHistoryService.registerFinalStatus(history, GlobalStatus.RETURNED);

        assertTrue(parcel.isIncludedInStatistics());
        assertEquals(1, storeStats.getTotalReturned());
        assertEquals(BigDecimal.valueOf(1.0), storeStats.getSumPickupDays());
        assertEquals(1, psStats.getTotalReturned());
        assertEquals(BigDecimal.valueOf(1.0), psStats.getSumPickupDays());
        assertEquals(1, dailyStats.getReturned());
        assertEquals(BigDecimal.valueOf(1.0), dailyStats.getSumPickupDays());
        assertEquals(1, psDaily.getReturned());
        assertEquals(BigDecimal.valueOf(1.0), psDaily.getSumPickupDays());
        verify(storeAnalyticsRepository).save(storeStats);
        verify(postalServiceStatisticsRepository).save(psStats);
        verify(storeDailyStatisticsRepository).save(dailyStats);
        verify(postalServiceDailyStatisticsRepository).save(psDaily);
        verify(trackParcelRepository).save(parcel);
    }

    @Test
    void handleTrackParcelBeforeDelete_DecrementsTotalSent() {
        Store store = new Store();
        store.setId(3L);
        TrackParcel parcel = new TrackParcel();
        parcel.setStore(store);
        parcel.setNumber("PC123456789BY");
        parcel.setIncludedInStatistics(false);
        parcel.setData(ZonedDateTime.of(2024, 1, 3, 0, 0, 0, 0, ZoneOffset.UTC));

        StoreStatistics storeStats = new StoreStatistics();
        storeStats.setTotalSent(5);
        PostalServiceStatistics psStats = new PostalServiceStatistics();
        psStats.setStore(store);
        psStats.setPostalServiceType(PostalServiceType.BELPOST);
        psStats.setTotalSent(2);
        StoreDailyStatistics dailyStats = new StoreDailyStatistics();
        dailyStats.setStore(store);
        dailyStats.setDate(parcel.getData().toLocalDate());
        dailyStats.setSent(5);
        PostalServiceDailyStatistics psDaily = new PostalServiceDailyStatistics();
        psDaily.setStore(store);
        psDaily.setPostalServiceType(PostalServiceType.BELPOST);
        psDaily.setDate(parcel.getData().toLocalDate());
        psDaily.setSent(2);

        when(storeAnalyticsRepository.findByStoreId(store.getId())).thenReturn(Optional.of(storeStats));
        when(typeDefinitionTrackPostService.detectPostalService(parcel.getNumber())).thenReturn(PostalServiceType.BELPOST);
        when(postalServiceStatisticsRepository.findByStoreIdAndPostalServiceType(store.getId(), PostalServiceType.BELPOST))
                .thenReturn(Optional.of(psStats));
        when(storeDailyStatisticsRepository.findByStoreIdAndDate(store.getId(), parcel.getData().toLocalDate())).thenReturn(Optional.of(dailyStats));
        when(postalServiceDailyStatisticsRepository.findByStoreIdAndPostalServiceTypeAndDate(store.getId(), PostalServiceType.BELPOST, parcel.getData().toLocalDate()))
                .thenReturn(Optional.of(psDaily));

        deliveryHistoryService.handleTrackParcelBeforeDelete(parcel);

        assertEquals(4, storeStats.getTotalSent());
        assertEquals(1, psStats.getTotalSent());
        assertEquals(4, dailyStats.getSent());
        assertEquals(1, psDaily.getSent());
        verify(storeAnalyticsRepository).save(storeStats);
        verify(postalServiceStatisticsRepository).save(psStats);
        verify(storeDailyStatisticsRepository).save(dailyStats);
        verify(postalServiceDailyStatisticsRepository).save(psDaily);
    }
}
