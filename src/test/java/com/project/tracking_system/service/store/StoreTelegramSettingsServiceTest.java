package com.project.tracking_system.service.store;

import com.project.tracking_system.controller.WebSocketController;
import com.project.tracking_system.dto.StoreTelegramSettingsDTO;
import com.project.tracking_system.entity.*;
import com.project.tracking_system.model.subscription.FeatureKey;
import com.project.tracking_system.repository.StoreTelegramSettingsRepository;
import com.project.tracking_system.service.store.StoreService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import com.project.tracking_system.exception.InvalidTemplateException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Тесты сохранения шаблонов сообщений.
 */
@ExtendWith(MockitoExtension.class)
class StoreTelegramSettingsServiceTest {

    @Mock
    private StoreTelegramSettingsRepository repository;
    @Mock
    private SubscriptionService subscriptionService;
    @Mock
    private WebSocketController webSocketController;
    @Mock
    private StoreService storeService;
    @InjectMocks
    private StoreTelegramSettingsService service;

    @Test
    void update_ValidTemplates_Saved() {
        Store store = new Store();
        store.setId(1L);
        StoreTelegramSettings settings = new StoreTelegramSettings();
        settings.setStore(store);
        when(repository.findByStoreId(1L)).thenReturn(settings);
        when(subscriptionService.isFeatureEnabled(1L, FeatureKey.TELEGRAM_NOTIFICATIONS)).thenReturn(true);

        StoreTelegramSettingsDTO dto = new StoreTelegramSettingsDTO();
        dto.setUseCustomTemplates(true);
        dto.setTemplates(Map.of("WAITING", "Msg {track} {store}"));

        service.update(store, dto, 1L);

        verify(repository).save(settings);
        assertEquals(1, settings.getTemplates().size());
    }

    @Test
    void update_InvalidTemplate_Throws() {
        Store store = new Store();
        store.setId(1L);
        StoreTelegramSettings settings = new StoreTelegramSettings();
        settings.setStore(store);
        when(repository.findByStoreId(1L)).thenReturn(settings);
        when(subscriptionService.isFeatureEnabled(1L, FeatureKey.TELEGRAM_NOTIFICATIONS)).thenReturn(true);

        StoreTelegramSettingsDTO dto = new StoreTelegramSettingsDTO();
        dto.setUseCustomTemplates(true);
        dto.setTemplates(Map.of("WAITING", "Неверный шаблон"));

        assertThrows(InvalidTemplateException.class, () -> service.update(store, dto, 1L));
    }
}
