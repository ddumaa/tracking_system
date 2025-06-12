import com.project.tracking_system.dto.TrackInfoDTO;
import com.project.tracking_system.dto.TrackInfoListDTO;
import com.project.tracking_system.entity.GlobalStatus;
import com.project.tracking_system.entity.Store;
import com.project.tracking_system.entity.TrackParcel;
import com.project.tracking_system.entity.User;
import com.project.tracking_system.repository.*;
import com.project.tracking_system.service.SubscriptionService;
import com.project.tracking_system.service.track.*;
import com.project.tracking_system.service.user.UserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class TrackPersistenceServiceTest2 {

    @Mock
    private TrackParcelRepository trackParcelRepository;
    @Mock
    private StoreRepository storeRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private UserService userService;
    @Mock
    private SubscriptionService subscriptionService;
    @Mock
    private UserSubscriptionRepository userSubscriptionRepository;
    @Mock
    private TrackAnalyticsService trackAnalyticsService;
    @Mock
    private StatusTrackService statusTrackService;
    @Mock
    private TypeDefinitionTrackPostService typeDefinitionTrackPostService;

    @InjectMocks
    private TrackPersistenceService service;

    @Test
    void save_NewParcel_CallsRepositoryAndAnalytics() {
        String number = "RR123";
        Long storeId = 1L;
        Long userId = 2L;

        TrackInfoDTO dto = new TrackInfoDTO("01.01.2024 00:00", "info");
        TrackInfoListDTO listDTO = new TrackInfoListDTO(List.of(dto));
        Store store = new Store();
        User user = new User();
        user.setTimeZone("UTC");

        when(trackParcelRepository.findByNumberAndUserId(number, userId)).thenReturn(null);
        when(subscriptionService.canSaveMoreTracks(userId, 1)).thenReturn(1);
        when(storeRepository.getReferenceById(storeId)).thenReturn(store);
        when(userRepository.getReferenceById(userId)).thenReturn(user);
        when(statusTrackService.setStatus(any())).thenReturn(GlobalStatus.REGISTERED);
        when(typeDefinitionTrackPostService.detectPostalService(number)).thenReturn(PostalServiceType.BELPOST);
        when(userService.getUserZone(userId)).thenReturn(ZoneId.of("UTC"));

        service.save(number, listDTO, storeId, userId);

        verify(trackParcelRepository).save(any(TrackParcel.class));
        verify(trackAnalyticsService).updateAnalytics(any(), eq(true), any(), any(), eq(PostalServiceType.BELPOST), any(), any(), any(), eq(listDTO));
    }
}
