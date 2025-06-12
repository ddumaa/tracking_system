import com.project.tracking_system.controller.WebSocketController;
import com.project.tracking_system.entity.UpdateResult;
import com.project.tracking_system.service.track.TrackNotificationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
public class TrackNotificationServiceTest {

    @Mock
    private WebSocketController webSocketController;

    @InjectMocks
    private TrackNotificationService service;

    @Test
    void notifyStatus_DelegatesToController() {
        service.notifyStatus(1L, "msg", true);
        verify(webSocketController).sendUpdateStatus(1L, "msg", true);
    }

    @Test
    void notifyDetailed_DelegatesToController() {
        UpdateResult result = new UpdateResult(true, "ok");
        service.notifyDetailed(2L, result);
        verify(webSocketController).sendDetailUpdateStatus(2L, result);
    }
}
