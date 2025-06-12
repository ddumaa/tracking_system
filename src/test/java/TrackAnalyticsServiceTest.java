import com.project.tracking_system.dto.TrackInfoDTO;
import com.project.tracking_system.dto.TrackInfoListDTO;
import com.project.tracking_system.entity.*;
import com.project.tracking_system.repository.*;
import com.project.tracking_system.service.analytics.DeliveryHistoryService;
import com.project.tracking_system.service.track.TrackAnalyticsService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.ZonedDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
public class TrackAnalyticsServiceTest {

    @Mock
    private StoreAnalyticsRepository storeAnalyticsRepository;
    @Mock
    private PostalServiceStatisticsRepository postalServiceStatisticsRepository;
    @Mock
    private StoreDailyStatisticsRepository storeDailyStatisticsRepository;
    @Mock
    private PostalServiceDailyStatisticsRepository postalServiceDailyStatisticsRepository;
    @Mock
    private DeliveryHistoryService deliveryHistoryService;

    @InjectMocks
    private TrackAnalyticsService service;

    @Test
    void updateAnalytics_CallsHistoryService() {
        TrackParcel parcel = new TrackParcel();
        parcel.setStore(new Store());
        TrackInfoDTO dto = new TrackInfoDTO("01.01", "info");
        TrackInfoListDTO listDTO = new TrackInfoListDTO(List.of(dto));
        service.updateAnalytics(parcel, true, null, null, PostalServiceType.BELPOST, ZonedDateTime.now(), null, GlobalStatus.REGISTERED, listDTO);
        verify(deliveryHistoryService).updateDeliveryHistory(eq(parcel), any(), eq(GlobalStatus.REGISTERED), eq(listDTO));
    }
}
