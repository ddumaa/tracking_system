package com.project.tracking_system.service.track;

import com.project.tracking_system.service.SubscriptionService;
import com.project.tracking_system.service.store.StoreService;
import com.project.tracking_system.entity.PostalServiceType;
import com.project.tracking_system.service.track.TypeDefinitionTrackPostService;
import com.project.tracking_system.service.customer.CustomerNameService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Тесты для {@link TrackMetaValidator}.
 * <p>
 * Проверяет корректную нормализацию данных и применение
 * пользовательских ограничений при валидации списка треков.
 * </p>
 */
@ExtendWith(MockitoExtension.class)
class TrackMetaValidatorTest {

    @Mock
    private TrackParcelService trackParcelService;
    @Mock
    private SubscriptionService subscriptionService;
    @Mock
    private StoreService storeService;
    @Mock
    private TypeDefinitionTrackPostService typeDefinitionTrackPostService;
    @Mock
    private CustomerNameService customerNameService;

    private TrackMetaValidator validator;

    @BeforeEach
    void setUp() {
        validator = new TrackMetaValidator(
                trackParcelService,
                subscriptionService,
                storeService,
                typeDefinitionTrackPostService,
                customerNameService
        );
    }

    /**
     * Проверяет применение лимита на сохранение новых треков.
     */
    @Test
    void validate_RespectsSaveLimit() {
        when(subscriptionService.canUploadTracks(anyLong(), anyInt())).thenReturn(2);
        when(subscriptionService.canSaveMoreTracks(anyLong(), anyInt())).thenReturn(1);
        when(storeService.getDefaultStoreId(1L)).thenReturn(1L);
        when(storeService.userOwnsStore(1L, 1L)).thenReturn(true);
        when(trackParcelService.isNewTrack(anyString(), any())).thenReturn(true);
        when(typeDefinitionTrackPostService.detectPostalService(anyString())).thenReturn(PostalServiceType.BELPOST);

        List<TrackExcelRow> rows = List.of(
                new TrackExcelRow("A1", "1", "375291111111", null, false),
                new TrackExcelRow("A2", "1", "375291111112", null, false)
        );

        TrackMetaValidationResult result = validator.validate(rows, 1L);

        assertEquals(2, result.validTracks().size());
        assertTrue(result.validTracks().get(0).canSave());
        assertFalse(result.validTracks().get(1).canSave());
        assertTrue(result.invalidTracks().isEmpty());
        assertNotNull(result.limitExceededMessage());
    }

    /**
     * Убеждается, что имя магазина корректно преобразуется в идентификатор.
     */
    @Test
    void validate_ParsesStoreName() {
        when(subscriptionService.canUploadTracks(anyLong(), anyInt())).thenReturn(1);
        when(subscriptionService.canSaveMoreTracks(anyLong(), anyInt())).thenReturn(1);
        when(storeService.getDefaultStoreId(1L)).thenReturn(1L);
        when(storeService.findStoreIdByName("Shop", 1L)).thenReturn(2L);
        when(storeService.userOwnsStore(2L, 1L)).thenReturn(true);
        when(trackParcelService.isNewTrack(anyString(), any())).thenReturn(true);
        when(typeDefinitionTrackPostService.detectPostalService(anyString())).thenReturn(PostalServiceType.BELPOST);

        List<TrackExcelRow> rows = List.of(
                new TrackExcelRow("A1", "Shop", "375291111111", null, false)
        );

        TrackMetaValidationResult result = validator.validate(rows, 1L);

        assertEquals(2L, result.validTracks().get(0).storeId());
        assertTrue(result.invalidTracks().isEmpty());
    }

    /**
     * Номер телефона клиента должен нормализоваться к числовому формату.
     */
    @Test
    void validate_NormalizesPhone() {
        when(subscriptionService.canUploadTracks(anyLong(), anyInt())).thenReturn(1);
        when(subscriptionService.canSaveMoreTracks(anyLong(), anyInt())).thenReturn(1);
        when(storeService.getDefaultStoreId(1L)).thenReturn(1L);
        when(storeService.userOwnsStore(1L, 1L)).thenReturn(true);
        when(trackParcelService.isNewTrack(anyString(), any())).thenReturn(true);
        when(typeDefinitionTrackPostService.detectPostalService(anyString())).thenReturn(PostalServiceType.BELPOST);

        List<TrackExcelRow> rows = List.of(
                new TrackExcelRow("A1", "1", "+375 (29) 111-11-11", null, false)
        );

        TrackMetaValidationResult result = validator.validate(rows, 1L);

        assertEquals("375291111111", result.validTracks().get(0).phone());
        assertTrue(result.invalidTracks().isEmpty());
    }

    /**
     * Некорректные строки должны собираться отдельно.
     */
    @Test
    void validate_CollectsInvalidRows() {
        when(subscriptionService.canUploadTracks(anyLong(), anyInt())).thenReturn(2);
        when(subscriptionService.canSaveMoreTracks(anyLong(), anyInt())).thenReturn(2);
        when(storeService.getDefaultStoreId(1L)).thenReturn(1L);
        when(storeService.userOwnsStore(1L, 1L)).thenReturn(true);
        when(trackParcelService.isNewTrack(anyString(), any())).thenReturn(true);
        when(typeDefinitionTrackPostService.detectPostalService(anyString()))
                .thenAnswer(invocation -> {
                    String num = invocation.getArgument(0);
                    return num.equals("BAD") ? PostalServiceType.UNKNOWN : PostalServiceType.BELPOST;
                });

        List<TrackExcelRow> rows = List.of(
                new TrackExcelRow("A1", "1", null, null, false),
                new TrackExcelRow("BAD", "1", null, null, false),
                new TrackExcelRow("A1", "1", null, null, false)
        );

        TrackMetaValidationResult result = validator.validate(rows, 1L);

        assertEquals(1, result.validTracks().size());
        assertEquals(2, result.invalidTracks().size());
    }

    /**
     * При наличии корректного телефона и имени вызывается сервис обновления ФИО.
     */
    @Test
    void validate_UpdatesCustomerName() {
        when(subscriptionService.canUploadTracks(anyLong(), anyInt())).thenReturn(1);
        when(subscriptionService.canSaveMoreTracks(anyLong(), anyInt())).thenReturn(1);
        when(storeService.getDefaultStoreId(1L)).thenReturn(1L);
        when(storeService.userOwnsStore(1L, 1L)).thenReturn(true);
        when(trackParcelService.isNewTrack(anyString(), any())).thenReturn(true);
        when(typeDefinitionTrackPostService.detectPostalService(anyString())).thenReturn(PostalServiceType.BELPOST);

        List<TrackExcelRow> rows = List.of(
                new TrackExcelRow("A1", "1", "+375291111111", "Иван", false)
        );

        validator.validate(rows, 1L);

        verify(customerNameService).upsertFromStore("375291111111", "Иван");
    }

    /**
     * Предрегистрации допускают отсутствие номера и возвращаются отдельно.
     */
    @Test
    void validate_HandlesPreRegisteredRows() {
        when(subscriptionService.canUploadTracks(anyLong(), anyInt())).thenReturn(0);
        when(subscriptionService.canSaveMoreTracks(anyLong(), anyInt())).thenReturn(0);
        when(storeService.getDefaultStoreId(1L)).thenReturn(1L);
        when(storeService.userOwnsStore(1L, 1L)).thenReturn(true);

        List<TrackExcelRow> rows = List.of(
                new TrackExcelRow(null, "1", null, null, true)
        );

        TrackMetaValidationResult result = validator.validate(rows, 1L);

        assertTrue(result.validTracks().isEmpty());
        assertTrue(result.invalidTracks().isEmpty());
        assertEquals(1, result.preRegistered().size());
        assertEquals(1L, result.preRegistered().get(0).storeId());
        assertNull(result.preRegistered().get(0).phone());
    }
}
