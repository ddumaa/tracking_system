package com.project.tracking_system.service.track;

import com.project.tracking_system.service.SubscriptionService;
import com.project.tracking_system.service.store.StoreService;
import com.project.tracking_system.entity.PostalServiceType;
import com.project.tracking_system.service.track.TypeDefinitionTrackPostService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

/**
 * Тесты для {@link TrackMetaValidator}.
 * <p>
 * Проверяется корректность нормализации данных и применение
 * пользовательских лимитов при валидации списка треков.
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

    private TrackMetaValidator validator;

    @BeforeEach
    void setUp() {
        validator = new TrackMetaValidator(
                trackParcelService,
                subscriptionService,
                storeService,
                typeDefinitionTrackPostService
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
                new TrackExcelRow("A1", "1", "375291111111"),
                new TrackExcelRow("A2", "1", "375291111112")
        );

        TrackMetaValidationResult result = validator.validate(rows, 1L);

        assertEquals(2, result.validTracks().size());
        assertTrue(result.validTracks().get(0).canSave());
        assertFalse(result.validTracks().get(1).canSave());
        assertTrue(result.invalidTracks().isEmpty());
        assertNotNull(result.limitExceededMessage());
    }

    /**
     * Убеждаемся, что имя магазина корректно преобразуется в идентификатор.
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
                new TrackExcelRow("A1", "Shop", "375291111111")
        );

        TrackMetaValidationResult result = validator.validate(rows, 1L);

        assertEquals(2L, result.validTracks().get(0).storeId());
        assertTrue(result.invalidTracks().isEmpty());
    }

    /**
     * Телефон клиента должен быть нормализован к цифровому формату.
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
                new TrackExcelRow("A1", "1", "+375 (29) 111-11-11")
        );

        TrackMetaValidationResult result = validator.validate(rows, 1L);

        assertEquals("375291111111", result.validTracks().get(0).phone());
        assertTrue(result.invalidTracks().isEmpty());
    }

    /**
     * Некорректные строки должны быть собраны отдельно.
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
                new TrackExcelRow("A1", "1", null),
                new TrackExcelRow("BAD", "1", null),
                new TrackExcelRow("A1", "1", null)
        );

        TrackMetaValidationResult result = validator.validate(rows, 1L);

        assertEquals(1, result.validTracks().size());
        assertEquals(2, result.invalidTracks().size());
    }
}
