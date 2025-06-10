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
import java.time.temporal.IsoFields;
import java.time.temporal.ChronoField;

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

        LocalDate weekStart = daily.getDate().with(ChronoField.DAY_OF_WEEK, 1);
        LocalDate weekEnd = daily.getDate().with(ChronoField.DAY_OF_WEEK, 7);
        LocalDate monthStart = daily.getDate().withDayOfMonth(1);
        LocalDate monthEnd = monthStart.plusMonths(1).minusDays(1);
        LocalDate yearStart = daily.getDate().withDayOfYear(1);
        LocalDate yearEnd = yearStart.plusYears(1).minusDays(1);

        when(storeDailyRepo.findByDate(daily.getDate())).thenReturn(List.of(daily));
        when(postalDailyRepo.findByDate(daily.getDate())).thenReturn(List.of());
        when(storeDailyRepo.findByStoreIdAndDateBetween(store.getId(), weekStart, weekEnd))
                .thenReturn(List.of(daily));
        when(storeDailyRepo.findByStoreIdAndDateBetween(store.getId(), monthStart, monthEnd))
                .thenReturn(List.of(daily));
        when(storeDailyRepo.findByStoreIdAndDateBetween(store.getId(), yearStart, yearEnd))
                .thenReturn(List.of(daily));
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

        LocalDate weekStartPs = daily.getDate().with(ChronoField.DAY_OF_WEEK, 1);
        LocalDate weekEndPs = daily.getDate().with(ChronoField.DAY_OF_WEEK, 7);
        LocalDate monthStartPs = daily.getDate().withDayOfMonth(1);
        LocalDate monthEndPs = monthStartPs.plusMonths(1).minusDays(1);
        LocalDate yearStartPs = daily.getDate().withDayOfYear(1);
        LocalDate yearEndPs = yearStartPs.plusYears(1).minusDays(1);

        when(storeDailyRepo.findByDate(daily.getDate())).thenReturn(List.of());
        when(postalDailyRepo.findByDate(daily.getDate())).thenReturn(List.of(daily));
        when(postalDailyRepo.findByStoreIdAndPostalServiceTypeAndDateBetween(store.getId(), daily.getPostalServiceType(), weekStartPs, weekEndPs))
                .thenReturn(List.of(daily));
        when(postalDailyRepo.findByStoreIdAndPostalServiceTypeAndDateBetween(store.getId(), daily.getPostalServiceType(), monthStartPs, monthEndPs))
                .thenReturn(List.of(daily));
        when(postalDailyRepo.findByStoreIdAndPostalServiceTypeAndDateBetween(store.getId(), daily.getPostalServiceType(), yearStartPs, yearEndPs))
                .thenReturn(List.of(daily));
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

    @Test
    void aggregateForDate_RepeatedCallsDoNotChangeResults() {
        Store store = new Store();
        store.setId(1L);
        StoreDailyStatistics daily = new StoreDailyStatistics();
        daily.setStore(store);
        daily.setDate(LocalDate.of(2024,1,2));
        daily.setSent(2);
        daily.setDelivered(1);
        daily.setReturned(0);

        LocalDate weekStart = daily.getDate().with(ChronoField.DAY_OF_WEEK, 1);
        LocalDate weekEnd = daily.getDate().with(ChronoField.DAY_OF_WEEK, 7);
        LocalDate monthStart = daily.getDate().withDayOfMonth(1);
        LocalDate monthEnd = monthStart.plusMonths(1).minusDays(1);
        LocalDate yearStart = daily.getDate().withDayOfYear(1);
        LocalDate yearEnd = yearStart.plusYears(1).minusDays(1);

        when(storeDailyRepo.findByDate(daily.getDate())).thenReturn(List.of(daily));
        when(postalDailyRepo.findByDate(daily.getDate())).thenReturn(List.of());
        when(storeDailyRepo.findByStoreIdAndDateBetween(store.getId(), weekStart, weekEnd)).thenReturn(List.of(daily));
        when(storeDailyRepo.findByStoreIdAndDateBetween(store.getId(), monthStart, monthEnd)).thenReturn(List.of(daily));
        when(storeDailyRepo.findByStoreIdAndDateBetween(store.getId(), yearStart, yearEnd)).thenReturn(List.of(daily));
        when(storeWeeklyRepo.findByStoreIdAndPeriodYearAndPeriodNumber(anyLong(), anyInt(), anyInt())).thenReturn(Optional.empty());
        when(storeMonthlyRepo.findByStoreIdAndPeriodYearAndPeriodNumber(anyLong(), anyInt(), anyInt())).thenReturn(Optional.empty());
        when(storeYearlyRepo.findByStoreIdAndPeriodYearAndPeriodNumber(anyLong(), anyInt(), anyInt())).thenReturn(Optional.empty());

        ArgumentCaptor<StoreWeeklyStatistics> wCap = ArgumentCaptor.forClass(StoreWeeklyStatistics.class);
        when(storeWeeklyRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.aggregateForDate(daily.getDate());
        service.aggregateForDate(daily.getDate());

        verify(storeWeeklyRepo, times(2)).save(wCap.capture());
        List<StoreWeeklyStatistics> saved = wCap.getAllValues();
        assertEquals(saved.get(0).getSent(), saved.get(1).getSent());
        assertEquals(saved.get(0).getDelivered(), saved.get(1).getDelivered());
        assertEquals(saved.get(0).getReturned(), saved.get(1).getReturned());
    }

    @Test
    void aggregateForRange_CallsDailyAggregationForEachDay() {
        StatsAggregationService spyService = spy(new StatsAggregationService(
                storeDailyRepo,
                postalDailyRepo,
                storeWeeklyRepo,
                storeMonthlyRepo,
                storeYearlyRepo,
                psWeeklyRepo,
                psMonthlyRepo,
                psYearlyRepo));

        doNothing().when(spyService).aggregateForDate(any());

        LocalDate from = LocalDate.of(2024, 1, 1);
        LocalDate to = LocalDate.of(2024, 1, 3);

        spyService.aggregateForRange(from, to);

        verify(spyService).aggregateForDate(LocalDate.of(2024, 1, 1));
        verify(spyService).aggregateForDate(LocalDate.of(2024, 1, 2));
        verify(spyService).aggregateForDate(LocalDate.of(2024, 1, 3));
    }
}
