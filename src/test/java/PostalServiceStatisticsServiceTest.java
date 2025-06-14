import com.project.tracking_system.dto.PostalServiceStatsDTO;
import com.project.tracking_system.entity.PostalServiceStatistics;
import com.project.tracking_system.entity.PostalServiceType;
import com.project.tracking_system.repository.PostalServiceStatisticsRepository;
import com.project.tracking_system.service.analytics.PostalServiceStatisticsService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class PostalServiceStatisticsServiceTest {

    @Mock
    private PostalServiceStatisticsRepository repository;

    @InjectMocks
    private PostalServiceStatisticsService service;

    @Test
    void getStatsByStore_FiltersZeroSent() {
        PostalServiceStatistics empty = new PostalServiceStatistics();
        empty.setPostalServiceType(PostalServiceType.BELPOST);
        empty.setTotalSent(0);

        PostalServiceStatistics nonEmpty = new PostalServiceStatistics();
        nonEmpty.setPostalServiceType(PostalServiceType.EVROPOST);
        nonEmpty.setTotalSent(5);

        when(repository.findByStoreId(1L)).thenReturn(List.of(empty, nonEmpty));

        List<PostalServiceStatsDTO> stats = service.getStatsByStore(1L);
        assertEquals(1, stats.size());
        assertEquals("Европочта", stats.get(0).getPostalService());
    }

    @Test
    void getStatsForStores_FiltersAfterAggregation() {
        PostalServiceStatistics empty = new PostalServiceStatistics();
        empty.setPostalServiceType(PostalServiceType.BELPOST);
        empty.setTotalSent(0);

        PostalServiceStatistics nonEmpty = new PostalServiceStatistics();
        nonEmpty.setPostalServiceType(PostalServiceType.EVROPOST);
        nonEmpty.setTotalSent(3);

        when(repository.findByStoreIdIn(List.of(1L, 2L))).thenReturn(List.of(empty, nonEmpty));

        List<PostalServiceStatsDTO> stats = service.getStatsForStores(List.of(1L, 2L));
        assertEquals(1, stats.size());
        assertEquals("Европочта", stats.get(0).getPostalService());
    }
}
