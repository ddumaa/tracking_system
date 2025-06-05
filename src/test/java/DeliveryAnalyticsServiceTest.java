import com.project.tracking_system.dto.DeliveryFullPeriodStatsDTO;
import com.project.tracking_system.entity.Store;
import com.project.tracking_system.entity.StoreDailyStatistics;
import com.project.tracking_system.repository.StoreAnalyticsRepository;
import com.project.tracking_system.repository.StoreDailyStatisticsRepository;
import com.project.tracking_system.service.analytics.DeliveryAnalyticsService;
import com.project.tracking_system.service.analytics.StoreAnalyticsService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class DeliveryAnalyticsServiceTest {

    @Mock
    private StoreAnalyticsRepository storeAnalyticsRepository;
    @Mock
    private StoreDailyStatisticsRepository storeDailyStatisticsRepository;
    @Mock
    private StoreAnalyticsService storeAnalyticsService;

    @InjectMocks
    private DeliveryAnalyticsService deliveryAnalyticsService;

    private StoreDailyStatistics createDaily(Store store, LocalDate date, int sent, int delivered, int returned) {
        StoreDailyStatistics d = new StoreDailyStatistics();
        d.setStore(store);
        d.setDate(date);
        d.setSent(sent);
        d.setDelivered(delivered);
        d.setReturned(returned);
        return d;
    }

    @Test
    void getFullPeriodStats_ByIntervals() {
        Store store = new Store();
        store.setId(1L);
        List<Long> storeIds = List.of(store.getId());
        ZoneId zone = ZoneId.of("UTC");
        ZonedDateTime from = ZonedDateTime.of(2024,1,1,0,0,0,0, zone);
        ZonedDateTime to = ZonedDateTime.of(2024,1,14,0,0,0,0, zone);

        List<StoreDailyStatistics> data = List.of(
                createDaily(store, LocalDate.of(2024,1,1),1,1,0),
                createDaily(store, LocalDate.of(2024,1,2),2,0,1),
                createDaily(store, LocalDate.of(2024,1,10),1,1,0)
        );
        when(storeDailyStatisticsRepository.findByStoreIdInAndDateBetween(storeIds, from.toLocalDate(), to.toLocalDate()))
                .thenReturn(data);

        List<DeliveryFullPeriodStatsDTO> byDay = deliveryAnalyticsService.getFullPeriodStats(storeIds, ChronoUnit.DAYS, from, to, zone);
        assertEquals(14, byDay.size());
        assertEquals(1, byDay.get(0).sent());

        List<DeliveryFullPeriodStatsDTO> byWeek = deliveryAnalyticsService.getFullPeriodStats(storeIds, ChronoUnit.WEEKS, from, to, zone);
        assertEquals(2, byWeek.size());
        assertEquals(3, byWeek.get(0).sent());
        assertEquals(1, byWeek.get(1).sent());

        List<DeliveryFullPeriodStatsDTO> byMonth = deliveryAnalyticsService.getFullPeriodStats(storeIds, ChronoUnit.MONTHS, from, to, zone);
        assertEquals(1, byMonth.size());
        assertEquals(4, byMonth.get(0).sent());

        List<DeliveryFullPeriodStatsDTO> byYear = deliveryAnalyticsService.getFullPeriodStats(storeIds, ChronoUnit.YEARS, from, to, zone);
        assertEquals(1, byYear.size());
        assertEquals(4, byYear.get(0).sent());
    }
}
