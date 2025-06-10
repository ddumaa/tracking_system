import com.project.tracking_system.dto.PeriodStatsDTO;
import com.project.tracking_system.dto.PeriodStatsSource;
import com.project.tracking_system.entity.Store;
import com.project.tracking_system.entity.StoreDailyStatistics;
import com.project.tracking_system.entity.StoreWeeklyStatistics;
import com.project.tracking_system.repository.StoreDailyStatisticsRepository;
import com.project.tracking_system.repository.StoreMonthlyStatisticsRepository;
import com.project.tracking_system.repository.StoreWeeklyStatisticsRepository;
import com.project.tracking_system.repository.StoreYearlyStatisticsRepository;
import com.project.tracking_system.service.analytics.PeriodDataResolver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.time.temporal.IsoFields;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class PeriodDataResolverTest {

    @Mock
    private StoreDailyStatisticsRepository dailyRepo;
    @Mock
    private StoreWeeklyStatisticsRepository weeklyRepo;
    @Mock
    private StoreMonthlyStatisticsRepository monthlyRepo;
    @Mock
    private StoreYearlyStatisticsRepository yearlyRepo;

    @InjectMocks
    private PeriodDataResolver resolver;

    private StoreWeeklyStatistics createWeekly(int sent) {
        StoreWeeklyStatistics w = new StoreWeeklyStatistics();
        w.setSent(sent);
        w.setDelivered(0);
        w.setReturned(0);
        return w;
    }

    private StoreDailyStatistics createDaily(LocalDate date, int sent) {
        StoreDailyStatistics d = new StoreDailyStatistics();
        d.setDate(date);
        d.setSent(sent);
        d.setDelivered(0);
        d.setReturned(0);
        d.setStore(new Store());
        return d;
    }

    @Test
    void resolve_UsesWeeklyAndDailyFallback() {
        ZoneId zone = ZoneId.of("UTC");
        List<Long> storeIds = List.of(1L);
        ZonedDateTime from = ZonedDateTime.of(2024,1,1,0,0,0,0, zone);
        ZonedDateTime to = ZonedDateTime.of(2024,1,14,0,0,0,0, zone);

        int week = from.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
        int year = from.get(IsoFields.WEEK_BASED_YEAR);
        when(weeklyRepo.findByStoreIdInAndPeriodYearAndPeriodNumber(storeIds, year, week))
                .thenReturn(List.of(createWeekly(3))); 
        // second week not available -> daily fallback
        LocalDate fromDaily = LocalDate.of(2024,1,8);
        LocalDate toDaily = LocalDate.of(2024,1,14);
        when(dailyRepo.findByStoreIdInAndDateBetween(storeIds, fromDaily, toDaily))
                .thenReturn(List.of(createDaily(LocalDate.of(2024,1,8),1)));

        List<PeriodStatsDTO> list = resolver.resolve(storeIds, ChronoUnit.WEEKS, from, to, zone);

        assertEquals(2, list.size());
        assertEquals(PeriodStatsSource.WEEKLY, list.get(0).source());
        assertEquals(3, list.get(0).sent());
        assertEquals(PeriodStatsSource.DAILY, list.get(1).source());
        assertEquals(1, list.get(1).sent());
    }
}
