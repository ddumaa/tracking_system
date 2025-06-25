package com.project.tracking_system.controller;

import com.project.tracking_system.entity.User;
import com.project.tracking_system.service.SubscriptionService;
import com.project.tracking_system.service.store.StoreService;
import com.project.tracking_system.service.user.UserService;
import com.project.tracking_system.model.subscription.FeatureKey;
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

    @Test
    void updateTelegramNotifications_FeatureDisabled_ReturnsForbidden() {
        User user = new User();
        user.setId(2L);
        when(subscriptionService.isFeatureEnabled(2L, FeatureKey.TELEGRAM_NOTIFICATIONS)).thenReturn(false);

        ResponseEntity<?> response = controller.updateTelegramNotifications(true, user);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        verify(userService, never()).updateTelegramNotificationsEnabled(anyLong(), anyBoolean());
    }
}
