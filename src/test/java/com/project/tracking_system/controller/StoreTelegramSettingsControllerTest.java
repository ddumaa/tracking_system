package com.project.tracking_system.controller;

import com.project.tracking_system.dto.StoreTelegramSettingsDTO;
import com.project.tracking_system.entity.Store;
import com.project.tracking_system.entity.StoreTelegramSettings;
import com.project.tracking_system.entity.User;
import com.project.tracking_system.repository.StoreTelegramSettingsRepository;
import com.project.tracking_system.repository.StoreTelegramTemplateRepository;
import com.project.tracking_system.service.SubscriptionService;
import com.project.tracking_system.service.store.StoreService;
import com.project.tracking_system.service.store.StoreTelegramSettingsService;
import com.project.tracking_system.service.telegram.TelegramBotValidationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Тесты для {@link StoreTelegramSettingsController}.
 */
@ExtendWith(MockitoExtension.class)
class StoreTelegramSettingsControllerTest {

    @Mock
    private StoreService storeService;
    @Mock
    private StoreTelegramSettingsRepository settingsRepository;
    @Mock
    private SubscriptionService subscriptionService;
    @Mock
    private StoreTelegramTemplateRepository templateRepository;
    @Mock
    private TelegramBotValidationService validationService;
    @Mock
    private WebSocketController webSocketController;

    @InjectMocks
    private StoreTelegramSettingsService telegramSettingsService;
    @InjectMocks
    private StoreTelegramSettingsController controller;

    @Test
    void addCustomBot_ValidToken_ReturnsUsername() {
        User user = new User();
        user.setId(1L);
        Store store = new Store();
        store.setId(1L);
        store.setOwner(user);
        StoreTelegramSettings settings = new StoreTelegramSettings();
        settings.setStore(store);
        store.setTelegramSettings(settings);

        when(storeService.getStore(1L, 1L)).thenReturn(store);
        when(subscriptionService.canUseCustomBot(1L)).thenReturn(true);
        when(settingsRepository.findByStoreId(1L)).thenReturn(settings);
        when(validationService.validateToken("token")).thenReturn("bot");
        when(settingsRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        StoreTelegramSettingsDTO dto = new StoreTelegramSettingsDTO();
        dto.setBotToken("token");
        dto.setBotUsername("bot");
        when(storeService.toDto(settings)).thenReturn(dto);

        ResponseEntity<?> response = controller.addCustomBot(1L, "token", user);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertSame(dto, response.getBody());
        assertEquals("bot", settings.getBotUsername());
    }

    @Test
    void addCustomBot_InvalidToken_ReturnsError() {
        User user = new User();
        user.setId(2L);
        Store store = new Store();
        store.setId(2L);
        store.setOwner(user);
        StoreTelegramSettings settings = new StoreTelegramSettings();
        settings.setStore(store);
        store.setTelegramSettings(settings);

        when(storeService.getStore(2L, 2L)).thenReturn(store);
        when(subscriptionService.canUseCustomBot(2L)).thenReturn(true);
        when(settingsRepository.findByStoreId(2L)).thenReturn(settings);
        when(validationService.validateToken("bad")).thenThrow(new IllegalArgumentException("bad"));

        ResponseEntity<?> response = controller.addCustomBot(2L, "bad", user);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        verify(settingsRepository, never()).save(any());
    }

    @Test
    void addCustomBot_FeatureDisabled_ReturnsForbidden() {
        User user = new User();
        user.setId(3L);
        when(subscriptionService.canUseCustomBot(3L)).thenReturn(false);
        when(storeService.getStore(3L, 3L)).thenReturn(new Store());

        ResponseEntity<?> response = controller.addCustomBot(3L, "t", user);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        verify(validationService, never()).validateToken(any());
    }

    @Test
    void deleteCustomBot_RemovesTokenAndUsername() {
        User user = new User();
        user.setId(4L);
        Store store = new Store();
        store.setId(4L);
        store.setOwner(user);
        StoreTelegramSettings settings = new StoreTelegramSettings();
        settings.setStore(store);
        settings.setBotToken("token");
        settings.setBotUsername("bot");
        store.setTelegramSettings(settings);

        when(storeService.getStore(4L, 4L)).thenReturn(store);
        when(settingsRepository.findByStoreId(4L)).thenReturn(settings);
        when(settingsRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        StoreTelegramSettingsDTO dto = new StoreTelegramSettingsDTO();
        when(storeService.toDto(settings)).thenReturn(dto);

        ResponseEntity<?> response = controller.deleteCustomBot(4L, user);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNull(settings.getBotToken());
        assertNull(settings.getBotUsername());
    }
}

