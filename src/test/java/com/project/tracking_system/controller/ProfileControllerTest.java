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
    private SubscriptionService subscriptionService;
    @Mock
    private StoreService storeService;

    @InjectMocks
    private ProfileController controller;

    @Test
    void profile_AddsAllowCustomTemplatesFlag() {
        User user = new User();
        user.setId(3L);

        when(userService.getUserStoreLimit(3L)).thenReturn("0/1");
        when(userService.getUserProfile(3L)).thenReturn(new com.project.tracking_system.dto.UserProfileDTO(null, null, null, null, null, false, new com.project.tracking_system.dto.SubscriptionPlanViewDTO(), 0, 0, 0));
        when(storeService.getUserStoresDto(3L)).thenReturn(java.util.Collections.emptyList());
        when(userService.isShowBulkUpdateButton(3L)).thenReturn(false);
        when(userService.isTelegramNotificationsEnabled(3L)).thenReturn(false);
        when(userService.getEvropostCredentials(3L)).thenReturn(new com.project.tracking_system.dto.EvropostCredentialsDTO());
        when(subscriptionService.canUseCustomNotifications(3L)).thenReturn(false);

        org.springframework.ui.ExtendedModelMap model = new org.springframework.ui.ExtendedModelMap();

        String view = controller.profile(model, user);

        assertEquals("profile", view);
        assertEquals(false, model.get("allowCustomTemplates"));
    }

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

    @Test
    void settings_AddsAllowCustomTemplatesFlag() {
        User user = new User();
        user.setId(4L);

        org.springframework.ui.ExtendedModelMap model = new org.springframework.ui.ExtendedModelMap();
        when(subscriptionService.canUseCustomNotifications(4L)).thenReturn(false);
        when(userService.isShowBulkUpdateButton(4L)).thenReturn(false);
        when(userService.isTelegramNotificationsEnabled(4L)).thenReturn(false);
        when(storeService.getUserStoresWithSettings(4L)).thenReturn(java.util.Collections.emptyList());

        String view = controller.settings("notifications", model, user);

        assertEquals("profile", view);
        assertEquals(false, model.get("allowCustomTemplates"));
    }
}
