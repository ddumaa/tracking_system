package com.project.tracking_system.controller;

import com.project.tracking_system.entity.User;
import com.project.tracking_system.service.store.StoreService;
import com.project.tracking_system.service.track.StatusTrackService;
import com.project.tracking_system.service.track.TrackFacade;
import com.project.tracking_system.service.track.TrackParcelService;
import com.project.tracking_system.service.track.TrackViewService;
import com.project.tracking_system.service.user.UserService;
import com.project.tracking_system.websocket.WebSocketController;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

/**
 * Тесты для {@link DeparturesController}.
 */
@ExtendWith(MockitoExtension.class)
class DeparturesControllerTest {

    @Mock
    private StatusTrackService statusTrackService;
    @Mock
    private TrackParcelService trackParcelService;
    @Mock
    private TrackFacade trackFacade;
    @Mock
    private TrackViewService trackViewService;
    @Mock
    private StoreService storeService;
    @Mock
    private UserService userService;
    @Mock
    private WebSocketController webSocketController;

    private DeparturesController controller;

    @BeforeEach
    void setUp() {
        controller = new DeparturesController(statusTrackService, trackParcelService,
                trackFacade, trackViewService, storeService, userService, webSocketController);
    }

    /**
     * Проверяет, что пустые номера игнорируются и удаление происходит по ID.
     */
    @Test
    void deleteSelected_IgnoresEmptyNumbersAndUsesIds() {
        User user = new User();
        user.setId(1L);

        List<String> numbers = List.of("", "");
        List<Long> ids = List.of(10L);

        ResponseEntity<?> response = controller.deleteSelected(numbers, ids, user);

        verify(trackFacade, never()).deleteByNumbersAndUserId(anyList(), anyLong());
        verify(trackFacade).deleteByIdsAndUserId(ids, 1L);
        assertEquals(HttpStatus.OK, response.getStatusCode());
    }
}

