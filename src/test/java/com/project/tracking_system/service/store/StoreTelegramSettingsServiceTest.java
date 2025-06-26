package com.project.tracking_system.service.store;

import com.project.tracking_system.dto.StoreTelegramSettingsDTO;
import com.project.tracking_system.entity.Store;
import com.project.tracking_system.entity.StoreTelegramSettings;
import com.project.tracking_system.model.subscription.FeatureKey;
import com.project.tracking_system.repository.StoreTelegramSettingsRepository;
import com.project.tracking_system.repository.StoreTelegramTemplateRepository;
import com.project.tracking_system.service.SubscriptionService;
import com.project.tracking_system.service.telegram.TelegramClientFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.telegram.telegrambots.meta.api.methods.GetMe;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.generics.TelegramClient;

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
    private TelegramClientFactory clientFactory;
    @Mock
    private TelegramClient telegramClient;

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
        when(clientFactory.create("token")).thenReturn(telegramClient);
        User me = new User();
        me.setUserName("bot");
        when(telegramClient.execute(any(GetMe.class))).thenReturn(me);

        service.update(store, dto, 2L);

        verify(storeService).updateFromDto(eq(settings), eq(dto));
        verify(settingsRepository).save(settings);
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
}
