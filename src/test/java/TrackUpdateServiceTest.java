import com.project.tracking_system.dto.TrackInfoDTO;
import com.project.tracking_system.dto.TrackInfoListDTO;
import com.project.tracking_system.dto.TrackParcelDTO;
import com.project.tracking_system.entity.UpdateResult;
import com.project.tracking_system.service.track.TrackNotificationService;
import com.project.tracking_system.service.track.TrackPersistenceService;
import com.project.tracking_system.service.track.TrackUpdateService;
import com.project.tracking_system.service.track.TypeDefinitionTrackPostService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class TrackUpdateServiceTest {

    @Mock
    private TypeDefinitionTrackPostService typeDefinitionTrackPostService;
    @Mock
    private TrackPersistenceService trackPersistenceService;
    @Mock
    private TrackNotificationService trackNotificationService;

    @InjectMocks
    private TrackUpdateService service;

    @Test
    void processTrack_SavesWhenDataExists() {
        TrackInfoDTO dto = new TrackInfoDTO("01", "info");
        TrackInfoListDTO info = new TrackInfoListDTO(List.of(dto));
        when(typeDefinitionTrackPostService.getTypeDefinitionTrackPostService(1L, "RR"))
                .thenReturn(info);
        service.processTrack("RR", 2L, 1L, true);
        verify(trackPersistenceService).save("RR", info, 2L, 1L);
    }
}
