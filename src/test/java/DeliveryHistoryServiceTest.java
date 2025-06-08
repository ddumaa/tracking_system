import com.project.tracking_system.entity.*;
import com.project.tracking_system.repository.PostalServiceStatisticsRepository;
import com.project.tracking_system.repository.StoreAnalyticsRepository;
import com.project.tracking_system.repository.TrackParcelRepository;
import com.project.tracking_system.repository.StoreDailyStatisticsRepository;
import com.project.tracking_system.repository.PostalServiceDailyStatisticsRepository;
import com.project.tracking_system.repository.DeliveryHistoryRepository;
import com.project.tracking_system.service.track.StatusTrackService;
import com.project.tracking_system.service.analytics.DeliveryHistoryService;
import com.project.tracking_system.service.track.TypeDefinitionTrackPostService;
import com.project.tracking_system.dto.TrackInfoDTO;
import com.project.tracking_system.dto.TrackInfoListDTO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.ZoneId;
import java.util.List;
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
    private DeliveryHistoryRepository deliveryHistoryRepository;
    @Mock
    private StatusTrackService statusTrackService;
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
        assertEquals(BigDecimal.valueOf(1.0), storeStats.getSumDeliveryDays());
        assertEquals(BigDecimal.valueOf(1.0), storeStats.getSumPickupDays());
        assertEquals(1, psStats.getTotalDelivered());
        assertEquals(BigDecimal.valueOf(1.0), psStats.getSumDeliveryDays());
        assertEquals(BigDecimal.valueOf(1.0), psStats.getSumPickupDays());
        assertEquals(1, dailyStats.getDelivered());
        assertEquals(BigDecimal.valueOf(1.0), dailyStats.getSumDeliveryDays());
        assertEquals(BigDecimal.valueOf(1.0), dailyStats.getSumPickupDays());
        assertEquals(1, psDaily.getDelivered());
        assertEquals(BigDecimal.valueOf(1.0), psDaily.getSumDeliveryDays());
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
        assertEquals(BigDecimal.ZERO, storeStats.getSumPickupDays());
        assertEquals(1, psStats.getTotalReturned());
        assertEquals(BigDecimal.ZERO, psStats.getSumPickupDays());
        assertEquals(1, dailyStats.getReturned());
        assertEquals(BigDecimal.ZERO, dailyStats.getSumPickupDays());
        assertEquals(1, psDaily.getReturned());
        assertEquals(BigDecimal.ZERO, psDaily.getSumPickupDays());
        verify(storeAnalyticsRepository).save(storeStats);
        verify(postalServiceStatisticsRepository).save(psStats);
        verify(storeDailyStatisticsRepository).save(dailyStats);
        verify(postalServiceDailyStatisticsRepository).save(psDaily);
        verify(trackParcelRepository).save(parcel);
    }

    @Test
    void registerFinalStatus_CreatesDailyStatsIfAbsent() {
        Store store = new Store();
        store.setId(5L);
        TrackParcel parcel = new TrackParcel();
        parcel.setStore(store);
        parcel.setIncludedInStatistics(false);
        DeliveryHistory history = new DeliveryHistory();
        history.setTrackParcel(parcel);
        history.setStore(store);
        history.setPostalService(PostalServiceType.BELPOST);
        ZonedDateTime send = ZonedDateTime.of(2024, 1, 4, 0, 0, 0, 0, ZoneOffset.UTC);
        ZonedDateTime arrived = send.plusDays(1);
        ZonedDateTime received = send.plusDays(2);
        history.setSendDate(send);
        history.setArrivedDate(arrived);
        history.setReceivedDate(received);

        StoreStatistics storeStats = new StoreStatistics();
        PostalServiceStatistics psStats = new PostalServiceStatistics();
        psStats.setStore(store);
        psStats.setPostalServiceType(PostalServiceType.BELPOST);

        when(storeAnalyticsRepository.findByStoreId(store.getId())).thenReturn(Optional.of(storeStats));
        when(postalServiceStatisticsRepository.findByStoreIdAndPostalServiceType(store.getId(), PostalServiceType.BELPOST))
                .thenReturn(Optional.of(psStats));
        when(storeDailyStatisticsRepository.findByStoreIdAndDate(store.getId(), received.toLocalDate()))
                .thenReturn(Optional.empty());
        when(postalServiceDailyStatisticsRepository.findByStoreIdAndPostalServiceTypeAndDate(store.getId(), PostalServiceType.BELPOST, received.toLocalDate()))
                .thenReturn(Optional.empty());

        ArgumentCaptor<StoreDailyStatistics> dailyCaptor = ArgumentCaptor.forClass(StoreDailyStatistics.class);
        ArgumentCaptor<PostalServiceDailyStatistics> psDailyCaptor = ArgumentCaptor.forClass(PostalServiceDailyStatistics.class);

        deliveryHistoryService.registerFinalStatus(history, GlobalStatus.DELIVERED);

        assertTrue(parcel.isIncludedInStatistics());
        verify(storeDailyStatisticsRepository).save(dailyCaptor.capture());
        verify(postalServiceDailyStatisticsRepository).save(psDailyCaptor.capture());

        StoreDailyStatistics savedDaily = dailyCaptor.getValue();
        assertEquals(1, savedDaily.getDelivered());
        assertEquals(received.toLocalDate(), savedDaily.getDate());

        PostalServiceDailyStatistics savedPsDaily = psDailyCaptor.getValue();
        assertEquals(1, savedPsDaily.getDelivered());
        assertEquals(received.toLocalDate(), savedPsDaily.getDate());
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

    @Test
    void updateDeliveryHistory_SetsArrivedDateOnWaiting() {
        Store store = new Store();
        store.setId(10L);
        TrackParcel parcel = new TrackParcel();
        parcel.setId(1L);
        parcel.setNumber("PC111");
        parcel.setStore(store);

        DeliveryHistory history = new DeliveryHistory();
        history.setTrackParcel(parcel);
        history.setStore(store);
        history.setPostalService(PostalServiceType.BELPOST);

        TrackInfoDTO waitInfo = new TrackInfoDTO("01.06.2024 12:00:00", "w");
        TrackInfoListDTO listDTO = new TrackInfoListDTO(List.of(waitInfo));

        when(deliveryHistoryRepository.findByTrackParcelId(parcel.getId())).thenReturn(Optional.of(history));
        when(typeDefinitionTrackPostService.detectPostalService(parcel.getNumber())).thenReturn(PostalServiceType.BELPOST);
        when(statusTrackService.setStatus(List.of(waitInfo))).thenReturn(GlobalStatus.WAITING_FOR_CUSTOMER);

        deliveryHistoryService.updateDeliveryHistory(parcel, GlobalStatus.IN_TRANSIT, GlobalStatus.WAITING_FOR_CUSTOMER, listDTO);

        ZonedDateTime expected = ZonedDateTime.of(2024,6,1,12,0,0,0, ZoneId.of("Europe/Minsk"))
                .withZoneSameInstant(ZoneOffset.UTC);
        assertEquals(expected, history.getArrivedDate());
        verify(deliveryHistoryRepository).save(history);
    }

    @Test
    void waitingThenDelivered_ComputesPickupDays() {
        Store store = new Store();
        store.setId(11L);
        TrackParcel parcel = new TrackParcel();
        parcel.setStore(store);
        parcel.setIncludedInStatistics(false);

        DeliveryHistory history = new DeliveryHistory();
        history.setTrackParcel(parcel);
        history.setStore(store);
        history.setPostalService(PostalServiceType.BELPOST);

        ZonedDateTime arrived = ZonedDateTime.of(2024,6,1,0,0,0,0, ZoneOffset.UTC);
        history.setArrivedDate(arrived);
        ZonedDateTime send = arrived.minusDays(1);
        ZonedDateTime received = arrived.plusDays(2);
        history.setSendDate(send);
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

        assertEquals(BigDecimal.valueOf(1.0), storeStats.getSumDeliveryDays());
        assertEquals(BigDecimal.valueOf(2.0), storeStats.getSumPickupDays());
        assertEquals(BigDecimal.valueOf(1.0), psStats.getSumDeliveryDays());
        assertEquals(BigDecimal.valueOf(2.0), psStats.getSumPickupDays());
    }
}
