import com.project.tracking_system.dto.PeriodStatsDTO;
import com.project.tracking_system.dto.PeriodStatsSource;
import com.project.tracking_system.service.analytics.DeliveryAnalyticsService;
import com.project.tracking_system.service.analytics.PeriodDataResolver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class DeliveryAnalyticsServiceTest {

    @Mock
    private PeriodDataResolver resolver;

    @InjectMocks
    private DeliveryAnalyticsService service;

    @Test
    void getFullPeriodStats_DelegatesToResolver() {
        List<Long> storeIds = List.of(1L);
        ZonedDateTime from = ZonedDateTime.now();
        ZonedDateTime to = from.plusDays(1);
        ZoneId zone = ZoneId.of("UTC");
        List<PeriodStatsDTO> expected = List.of(new PeriodStatsDTO("p",1,1,0, PeriodStatsSource.DAILY));
        when(resolver.resolve(storeIds, ChronoUnit.DAYS, from, to, zone)).thenReturn(expected);

        List<PeriodStatsDTO> result = service.getFullPeriodStats(storeIds, ChronoUnit.DAYS, from, to, zone);

        assertSame(expected, result);
        verify(resolver).resolve(storeIds, ChronoUnit.DAYS, from, to, zone);
    }
}
