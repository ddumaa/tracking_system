package com.project.tracking_system.controller;

import com.project.tracking_system.entity.User;
import com.project.tracking_system.service.SubscriptionService;
import com.project.tracking_system.service.store.StoreService;
import com.project.tracking_system.service.user.UserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

/**
 * Тесты для {@link ProfileController}.
 */
@ExtendWith(MockitoExtension.class)
class ProfileControllerTest {

    @Mock
    private UserService userService;
    @Mock
    private StoreService storeService;
    @Mock
    private WebSocketController webSocketController;
    @Mock
    private SubscriptionService subscriptionService;

    @InjectMocks
    private ProfileController controller;

    @Test
    void updateAutoUpdate_FeatureDisabled_ReturnsForbidden() {
        User user = new User();
        user.setId(1L);
        when(subscriptionService.canUseAutoUpdate(1L)).thenReturn(false);

        ResponseEntity<?> response = controller.updateAutoUpdate(true, user);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        verify(userService, never()).updateAutoUpdateEnabled(anyLong(), anyBoolean());
    }
}
