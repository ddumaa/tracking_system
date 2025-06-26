package com.project.tracking_system.service.store;

import com.project.tracking_system.dto.StoreTelegramSettingsDTO;
import com.project.tracking_system.entity.Store;
import com.project.tracking_system.entity.StoreTelegramSettings;
import com.project.tracking_system.model.subscription.FeatureKey;
import com.project.tracking_system.repository.StoreTelegramSettingsRepository;
import com.project.tracking_system.repository.StoreTelegramTemplateRepository;
import com.project.tracking_system.service.SubscriptionService;
import com.project.tracking_system.service.telegram.TelegramBotValidationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Тесты для {@link StoreTelegramSettingsService}.
 */
@ExtendWith(MockitoExtension.class)
class StoreTelegramSettingsServiceTest {

    @Mock
    private StoreTelegramSettingsRepository settingsRepository;
    @Mock
    private SubscriptionService subscriptionService;
    @Mock
    private StoreService storeService;
    @Mock
    private StoreTelegramTemplateRepository templateRepository;
    @Mock
    private TelegramBotValidationService validationService;

    @InjectMocks
    private StoreTelegramSettingsService service;

    @Test
    void update_TokenWithoutFeature_Throws() {
        Store store = new Store();
        StoreTelegramSettingsDTO dto = new StoreTelegramSettingsDTO();
        dto.setEnabled(false);
        dto.setBotToken("t");

        when(subscriptionService.isFeatureEnabled(1L, FeatureKey.CUSTOM_BOT)).thenReturn(false);
        StoreTelegramSettings settings = new StoreTelegramSettings();
        when(settingsRepository.findByStoreId(null)).thenReturn(settings);

        assertThrows(IllegalStateException.class, () -> service.update(store, dto, 1L));

        verify(subscriptionService).isFeatureEnabled(1L, FeatureKey.CUSTOM_BOT);
        verify(storeService, never()).updateFromDto(any(), any());
        verify(settingsRepository, never()).save(any());
    }

    @Test
    void update_ValidToken_SavesUsername() throws Exception {
        Store store = new Store();
        store.setId(2L);
        StoreTelegramSettings settings = new StoreTelegramSettings();
        when(settingsRepository.findByStoreId(2L)).thenReturn(settings);

        StoreTelegramSettingsDTO dto = new StoreTelegramSettingsDTO();
        dto.setEnabled(false);
        dto.setBotToken("token");

        when(subscriptionService.isFeatureEnabled(2L, FeatureKey.CUSTOM_BOT)).thenReturn(true);
        when(validationService.validateToken("token")).thenReturn("bot");

        service.update(store, dto, 2L);

        verify(storeService).updateFromDto(eq(settings), eq(dto));
        verify(settingsRepository).save(settings);
    }

    @Test
    void update_InvalidToken_Throws() {
        Store store = new Store();
        store.setId(3L);
        StoreTelegramSettings settings = new StoreTelegramSettings();
        when(settingsRepository.findByStoreId(3L)).thenReturn(settings);

        StoreTelegramSettingsDTO dto = new StoreTelegramSettingsDTO();
        dto.setEnabled(false);
        dto.setBotToken("bad");

        when(subscriptionService.isFeatureEnabled(3L, FeatureKey.CUSTOM_BOT)).thenReturn(true);
        when(validationService.validateToken("bad")).thenThrow(new IllegalArgumentException());

        assertThrows(IllegalArgumentException.class, () -> service.update(store, dto, 3L));

        verify(storeService, never()).updateFromDto(any(), any());
        verify(settingsRepository, never()).save(any());
    }

    @Test
    void update_NoToken_FeatureDisabled_AllowsUpdate() {
        Store store = new Store();
        store.setId(5L);
        StoreTelegramSettings settings = new StoreTelegramSettings();
        when(settingsRepository.findByStoreId(5L)).thenReturn(settings);

        StoreTelegramSettingsDTO dto = new StoreTelegramSettingsDTO();
        dto.setEnabled(false);

        service.update(store, dto, 5L);

        verify(storeService).updateFromDto(eq(settings), eq(dto));
        verify(settingsRepository).save(settings);
        verify(subscriptionService, never()).isFeatureEnabled(anyLong(), eq(FeatureKey.CUSTOM_BOT));
    }

    @Test
    void setCustomBot_FeatureDisabled_Throws() {
        Store store = new Store();
        store.setId(7L);

        when(subscriptionService.isFeatureEnabled(7L, FeatureKey.CUSTOM_BOT)).thenReturn(false);

        assertThrows(IllegalStateException.class, () -> service.setCustomBot(store, "t", 7L));

        verify(subscriptionService).isFeatureEnabled(7L, FeatureKey.CUSTOM_BOT);
        verify(settingsRepository, never()).save(any());
    }

    @Test
    void setCustomBot_ValidToken_Saves() {
        Store store = new Store();
        store.setId(8L);
        StoreTelegramSettings settings = new StoreTelegramSettings();
        when(settingsRepository.findByStoreId(8L)).thenReturn(settings);

        when(subscriptionService.isFeatureEnabled(8L, FeatureKey.CUSTOM_BOT)).thenReturn(true);
        when(validationService.validateToken("token")).thenReturn("bot");

        service.setCustomBot(store, "token", 8L);

        assertEquals("token", settings.getBotToken());
        assertEquals("bot", settings.getBotUsername());
        verify(settingsRepository).save(settings);
    }

    @Test
    void deleteCustomBot_FeatureDisabled_Throws() {
        Store store = new Store();
        store.setId(9L);

        when(subscriptionService.isFeatureEnabled(9L, FeatureKey.CUSTOM_BOT)).thenReturn(false);

        assertThrows(IllegalStateException.class, () -> service.deleteCustomBot(store, 9L));

        verify(subscriptionService).isFeatureEnabled(9L, FeatureKey.CUSTOM_BOT);
        verify(settingsRepository, never()).save(any());
    }

    @Test
    void deleteCustomBot_ClearsToken() {
        Store store = new Store();
        store.setId(10L);
        StoreTelegramSettings settings = new StoreTelegramSettings();
        settings.setBotToken("t");
        settings.setBotUsername("b");
        when(settingsRepository.findByStoreId(10L)).thenReturn(settings);

        when(subscriptionService.isFeatureEnabled(10L, FeatureKey.CUSTOM_BOT)).thenReturn(true);

        service.deleteCustomBot(store, 10L);

        assertNull(settings.getBotToken());
        assertNull(settings.getBotUsername());
        verify(settingsRepository).save(settings);
    }
}
