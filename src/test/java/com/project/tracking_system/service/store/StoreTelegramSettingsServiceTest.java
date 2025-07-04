package com.project.tracking_system.service.store;

import com.project.tracking_system.controller.WebSocketController;
import com.project.tracking_system.dto.StoreTelegramSettingsDTO;
import com.project.tracking_system.entity.Store;
import com.project.tracking_system.entity.StoreTelegramSettings;
import com.project.tracking_system.model.subscription.FeatureKey;
import com.project.tracking_system.repository.StoreTelegramSettingsRepository;
import com.project.tracking_system.service.SubscriptionService;
import com.project.tracking_system.exception.InvalidTemplateException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Проверка {@link StoreTelegramSettingsService}.
 */
@ExtendWith(MockitoExtension.class)
class StoreTelegramSettingsServiceTest {

    @Mock
    private StoreTelegramSettingsRepository settingsRepository;
    @Mock
    private SubscriptionService subscriptionService;
    @Mock
    private WebSocketController webSocketController;
    @Mock
    private StoreService storeService;

    @InjectMocks
    private StoreTelegramSettingsService service;

    @Test
    void update_CustomTemplatesFeatureDisabled_Throws() {
        Store store = new Store();
        store.setId(1L);
        StoreTelegramSettingsDTO dto = new StoreTelegramSettingsDTO();
        dto.setUseCustomTemplates(true);

        when(subscriptionService.isFeatureEnabled(1L, FeatureKey.TELEGRAM_NOTIFICATIONS)).thenReturn(true);
        when(subscriptionService.canUseCustomNotifications(1L)).thenReturn(false);

        assertThrows(IllegalStateException.class, () -> service.update(store, dto, 1L));
        verify(storeService, never()).updateFromDto(any(), any());
    }

    @Test
    void update_CustomTemplatesAllowed_Saves() {
        Store store = new Store();
        store.setId(2L);
        StoreTelegramSettingsDTO dto = new StoreTelegramSettingsDTO();
        dto.setUseCustomTemplates(true);

        when(subscriptionService.isFeatureEnabled(2L, FeatureKey.TELEGRAM_NOTIFICATIONS)).thenReturn(true);
        when(subscriptionService.canUseCustomNotifications(2L)).thenReturn(true);
        when(settingsRepository.findByStoreId(2L)).thenReturn(null);
        when(settingsRepository.save(any(StoreTelegramSettings.class))).thenAnswer(inv -> inv.getArgument(0));

        service.update(store, dto, 2L);

        verify(storeService).updateFromDto(any(StoreTelegramSettings.class), eq(dto));
        verify(settingsRepository).save(any(StoreTelegramSettings.class));
    }

    @Test
    void update_InvalidReminderTemplate_Throws() {
        Store store = new Store();
        store.setId(3L);
        StoreTelegramSettingsDTO dto = new StoreTelegramSettingsDTO();
        dto.setUseCustomTemplates(true);
        dto.setReminderTemplate("Неверный шаблон");

        when(subscriptionService.isFeatureEnabled(3L, FeatureKey.TELEGRAM_NOTIFICATIONS)).thenReturn(true);
        when(subscriptionService.canUseCustomNotifications(3L)).thenReturn(true);
        when(settingsRepository.findByStoreId(3L)).thenReturn(null);

        assertThrows(InvalidTemplateException.class, () -> service.update(store, dto, 3L));
        verify(settingsRepository, never()).save(any());
    }
}
