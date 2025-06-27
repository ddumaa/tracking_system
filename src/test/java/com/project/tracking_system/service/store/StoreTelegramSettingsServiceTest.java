package com.project.tracking_system.service.store;

import com.project.tracking_system.controller.WebSocketController;
import com.project.tracking_system.dto.StoreTelegramSettingsDTO;
import com.project.tracking_system.entity.Store;
import com.project.tracking_system.entity.StoreTelegramSettings;
import com.project.tracking_system.exception.InvalidTemplateException;
import com.project.tracking_system.model.subscription.FeatureKey;
import com.project.tracking_system.repository.StoreTelegramSettingsRepository;
import com.project.tracking_system.repository.StoreTelegramTemplateRepository;
import com.project.tracking_system.service.SubscriptionService;
import com.project.tracking_system.service.telegram.TelegramBotValidationService;
import com.project.tracking_system.service.store.StoreService;
import com.project.tracking_system.service.store.StoreTelegramSettingsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
    private WebSocketController webSocketController;
    @Mock
    private StoreService storeService;
    @Mock
    private StoreTelegramTemplateRepository storeTelegramTemplateRepository;
    @Mock
    private TelegramBotValidationService botValidationService;

    @InjectMocks
    private StoreTelegramSettingsService service;

    private Store store;

    @BeforeEach
    void setUp() {
        store = new Store();
        store.setId(1L);
    }

    @Test
    void update_SystemBot_RequiresStorePlaceholder() {
        StoreTelegramSettingsDTO dto = new StoreTelegramSettingsDTO();
        dto.setUseCustomTemplates(true);
        dto.getTemplates().put("SENT", "Посылка {track}");

        when(settingsRepository.findByStoreId(1L)).thenReturn(new StoreTelegramSettings());
        when(subscriptionService.isFeatureEnabled(1L, FeatureKey.TELEGRAM_NOTIFICATIONS)).thenReturn(true);
        when(subscriptionService.canUseCustomNotifications(1L)).thenReturn(true);

        assertThrows(InvalidTemplateException.class, () -> service.update(store, dto, 1L));
    }

    @Test
    void update_CustomBot_StorePlaceholderOptional() {
        StoreTelegramSettingsDTO dto = new StoreTelegramSettingsDTO();
        dto.setBotToken("token");
        dto.setUseCustomTemplates(true);
        dto.getTemplates().put("SENT", "Посылка {track}");

        when(settingsRepository.findByStoreId(1L)).thenReturn(new StoreTelegramSettings());
        when(subscriptionService.isFeatureEnabled(1L, FeatureKey.TELEGRAM_NOTIFICATIONS)).thenReturn(true);
        when(subscriptionService.canUseCustomNotifications(1L)).thenReturn(true);
        when(subscriptionService.canUseCustomBot(1L)).thenReturn(true);
        when(botValidationService.validateToken("token")).thenReturn("bot");

        assertDoesNotThrow(() -> service.update(store, dto, 1L));
    }

    @Test
    void update_RemindersDisabled_TemplateIgnored() {
        StoreTelegramSettingsDTO dto = new StoreTelegramSettingsDTO();
        dto.setRemindersEnabled(false);
        dto.setReminderTemplate("Неверный шаблон");

        when(settingsRepository.findByStoreId(1L)).thenReturn(new StoreTelegramSettings());
        when(subscriptionService.isFeatureEnabled(1L, FeatureKey.TELEGRAM_NOTIFICATIONS)).thenReturn(true);
        when(subscriptionService.canUseCustomNotifications(1L)).thenReturn(true);

        assertDoesNotThrow(() -> service.update(store, dto, 1L));
    }

    @Test
    void update_CustomTemplatesDisabled_TemplatesNotValidated() {
        StoreTelegramSettingsDTO dto = new StoreTelegramSettingsDTO();
        dto.setUseCustomTemplates(false);
        dto.getTemplates().put("SENT", "Без треков");

        when(settingsRepository.findByStoreId(1L)).thenReturn(new StoreTelegramSettings());
        when(subscriptionService.isFeatureEnabled(1L, FeatureKey.TELEGRAM_NOTIFICATIONS)).thenReturn(true);

        assertDoesNotThrow(() -> service.update(store, dto, 1L));
    }

    @Test
    void update_ExistingCustomBot_TokenNotProvided_BotRemains() {
        StoreTelegramSettings settings = new StoreTelegramSettings();
        settings.setBotToken("token");
        settings.setBotUsername("bot");
        when(settingsRepository.findByStoreId(1L)).thenReturn(settings);
        when(subscriptionService.isFeatureEnabled(1L, FeatureKey.TELEGRAM_NOTIFICATIONS)).thenReturn(true);
        when(subscriptionService.canUseCustomNotifications(1L)).thenReturn(true);

        StoreTelegramSettingsDTO dto = new StoreTelegramSettingsDTO();
        dto.setUseCustomTemplates(true);
        dto.getTemplates().put("SENT", "Посылка {track}");

        assertDoesNotThrow(() -> service.update(store, dto, 1L));
        verify(settingsRepository).save(settings);
        org.junit.jupiter.api.Assertions.assertEquals("token", settings.getBotToken());
    }
}
