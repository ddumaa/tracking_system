import com.project.tracking_system.entity.*;
import com.project.tracking_system.repository.*;
import com.project.tracking_system.service.analytics.StatsAggregationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class StatsAggregationServiceTest {

    @Mock
    private StoreDailyStatisticsRepository storeDailyRepo;
    @Mock
    private PostalServiceDailyStatisticsRepository postalDailyRepo;
    @Mock
    private StoreWeeklyStatisticsRepository storeWeeklyRepo;
    @Mock
    private StoreMonthlyStatisticsRepository storeMonthlyRepo;
    @Mock
    private StoreYearlyStatisticsRepository storeYearlyRepo;
    @Mock
    private PostalServiceWeeklyStatisticsRepository psWeeklyRepo;
    @Mock
    private PostalServiceMonthlyStatisticsRepository psMonthlyRepo;
    @Mock
    private PostalServiceYearlyStatisticsRepository psYearlyRepo;

    @InjectMocks
    private StatsAggregationService service;

    @Test
    void aggregateForDate_StoresAggregatedValues() {
        Store store = new Store();
        store.setId(1L);
        StoreDailyStatistics daily = new StoreDailyStatistics();
        daily.setStore(store);
        daily.setDate(LocalDate.of(2024,1,2));
        daily.setSent(2);
        daily.setDelivered(1);
        daily.setReturned(1);
        daily.setSumDeliveryDays(BigDecimal.valueOf(3));
        daily.setSumPickupDays(BigDecimal.valueOf(1));

        when(storeDailyRepo.findByDate(daily.getDate())).thenReturn(List.of(daily));
        when(postalDailyRepo.findByDate(daily.getDate())).thenReturn(List.of());
        when(storeWeeklyRepo.findByStoreIdAndPeriodYearAndPeriodNumber(anyLong(), anyInt(), anyInt())).thenReturn(Optional.empty());
        when(storeMonthlyRepo.findByStoreIdAndPeriodYearAndPeriodNumber(anyLong(), anyInt(), anyInt())).thenReturn(Optional.empty());
        when(storeYearlyRepo.findByStoreIdAndPeriodYearAndPeriodNumber(anyLong(), anyInt(), anyInt())).thenReturn(Optional.empty());

        ArgumentCaptor<StoreWeeklyStatistics> wCap = ArgumentCaptor.forClass(StoreWeeklyStatistics.class);
        ArgumentCaptor<StoreMonthlyStatistics> mCap = ArgumentCaptor.forClass(StoreMonthlyStatistics.class);
        ArgumentCaptor<StoreYearlyStatistics> yCap = ArgumentCaptor.forClass(StoreYearlyStatistics.class);

        when(storeWeeklyRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(storeMonthlyRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(storeYearlyRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.aggregateForDate(daily.getDate());

        verify(storeWeeklyRepo).save(wCap.capture());
        verify(storeMonthlyRepo).save(mCap.capture());
        verify(storeYearlyRepo).save(yCap.capture());

        assertEquals(2, wCap.getValue().getSent());
        assertEquals(1, mCap.getValue().getDelivered());
        assertEquals(1, yCap.getValue().getReturned());
    }

    @Test
    void aggregateForDate_PostalServiceAggregated() {
        Store store = new Store();
        store.setId(1L);
        PostalServiceDailyStatistics daily = new PostalServiceDailyStatistics();
        daily.setStore(store);
        daily.setPostalServiceType(PostalServiceType.BELPOST);
        daily.setDate(LocalDate.of(2024,1,2));
        daily.setSent(1);
        daily.setDelivered(1);
        daily.setReturned(0);
        daily.setSumDeliveryDays(BigDecimal.valueOf(2));
        daily.setSumPickupDays(BigDecimal.valueOf(1));

        when(storeDailyRepo.findByDate(daily.getDate())).thenReturn(List.of());
        when(postalDailyRepo.findByDate(daily.getDate())).thenReturn(List.of(daily));
        when(psWeeklyRepo.findByStoreIdAndPostalServiceTypeAndPeriodYearAndPeriodNumber(anyLong(), any(), anyInt(), anyInt())).thenReturn(Optional.empty());
        when(psMonthlyRepo.findByStoreIdAndPostalServiceTypeAndPeriodYearAndPeriodNumber(anyLong(), any(), anyInt(), anyInt())).thenReturn(Optional.empty());
        when(psYearlyRepo.findByStoreIdAndPostalServiceTypeAndPeriodYearAndPeriodNumber(anyLong(), any(), anyInt(), anyInt())).thenReturn(Optional.empty());

        ArgumentCaptor<PostalServiceWeeklyStatistics> wCap = ArgumentCaptor.forClass(PostalServiceWeeklyStatistics.class);
        when(psWeeklyRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(psMonthlyRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(psYearlyRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.aggregateForDate(daily.getDate());

        verify(psWeeklyRepo).save(wCap.capture());
        assertEquals(1, wCap.getValue().getDelivered());
    }
}
