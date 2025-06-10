import com.project.tracking_system.repository.*;
import com.project.tracking_system.service.analytics.AnalyticsResetService;
import com.project.tracking_system.service.store.StoreService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
public class AnalyticsResetServiceTest {

    @Mock
    private StoreAnalyticsRepository storeAnalyticsRepository;
    @Mock
    private PostalServiceStatisticsRepository postalStatisticsRepository;
    @Mock
    private StoreDailyStatisticsRepository storeDailyRepo;
    @Mock
    private StoreWeeklyStatisticsRepository storeWeeklyRepo;
    @Mock
    private StoreMonthlyStatisticsRepository storeMonthlyRepo;
    @Mock
    private StoreYearlyStatisticsRepository storeYearlyRepo;
    @Mock
    private PostalServiceDailyStatisticsRepository psDailyRepo;
    @Mock
    private PostalServiceWeeklyStatisticsRepository psWeeklyRepo;
    @Mock
    private PostalServiceMonthlyStatisticsRepository psMonthlyRepo;
    @Mock
    private PostalServiceYearlyStatisticsRepository psYearlyRepo;
    @Mock
    private StoreService storeService;

    @InjectMocks
    private AnalyticsResetService service;

    @Test
    void resetAllAnalytics_ResetsAllRepositories() {
        service.resetAllAnalytics(1L);

        verify(storeAnalyticsRepository).resetByUserId(1L);
        verify(postalStatisticsRepository).resetByUserId(1L);
        verify(storeDailyRepo).deleteByUserId(1L);
        verify(storeWeeklyRepo).deleteByUserId(1L);
        verify(storeMonthlyRepo).deleteByUserId(1L);
        verify(storeYearlyRepo).deleteByUserId(1L);
        verify(psDailyRepo).deleteByUserId(1L);
        verify(psWeeklyRepo).deleteByUserId(1L);
        verify(psMonthlyRepo).deleteByUserId(1L);
        verify(psYearlyRepo).deleteByUserId(1L);
    }

    @Test
    void resetStoreAnalytics_ChecksOwnershipAndResets() {
        service.resetStoreAnalytics(2L, 3L);

        verify(storeService).checkStoreOwnership(3L, 2L);
        verify(storeAnalyticsRepository).resetByStoreId(3L);
        verify(postalStatisticsRepository).resetByStoreId(3L);
        verify(storeDailyRepo).deleteByStoreId(3L);
        verify(storeWeeklyRepo).deleteByStoreId(3L);
        verify(storeMonthlyRepo).deleteByStoreId(3L);
        verify(storeYearlyRepo).deleteByStoreId(3L);
        verify(psDailyRepo).deleteByStoreId(3L);
        verify(psWeeklyRepo).deleteByStoreId(3L);
        verify(psMonthlyRepo).deleteByStoreId(3L);
        verify(psYearlyRepo).deleteByStoreId(3L);
    }
}
