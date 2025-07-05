package com.project.tracking_system.controller;

import com.project.tracking_system.dto.StoreTelegramSettingsDTO;
import com.project.tracking_system.entity.User;
import com.project.tracking_system.service.SubscriptionService;
import com.project.tracking_system.service.store.StoreService;
import com.project.tracking_system.repository.StoreTelegramSettingsRepository;
import com.project.tracking_system.service.store.StoreTelegramSettingsService;
import com.project.tracking_system.model.subscription.FeatureKey;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BeanPropertyBindingResult;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Проверка {@link StoreTelegramSettingsController}.
 */
@ExtendWith(MockitoExtension.class)
class StoreTelegramSettingsControllerTest {

    @Mock
    private StoreService storeService;
    @Mock
    private StoreTelegramSettingsRepository settingsRepository;
    @Mock
    private StoreTelegramSettingsService telegramSettingsService;
    @Mock
    private WebSocketController webSocketController;
    @Mock
    private SubscriptionService subscriptionService;

    @InjectMocks
    private StoreTelegramSettingsController controller;

    @Test
    void updateSettings_CustomTemplatesFeatureDisabled_ReturnsForbidden() {
        User user = new User();
        user.setId(1L);
        StoreTelegramSettingsDTO dto = new StoreTelegramSettingsDTO();
        dto.setCustomTemplates(true);

        BeanPropertyBindingResult binding = new BeanPropertyBindingResult(dto, "dto");
        when(subscriptionService.canUseCustomNotifications(1L)).thenReturn(false);

        ResponseEntity<?> response = controller.updateSettings(1L, dto, binding, user);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        verify(telegramSettingsService, never()).update(any(), any(), anyLong());
    }
}
