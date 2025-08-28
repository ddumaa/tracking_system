package com.project.tracking_system.service.track;

import com.project.tracking_system.entity.TrackParcel;
import com.project.tracking_system.entity.DeliveryHistory;
import com.project.tracking_system.entity.GlobalStatus;
import com.project.tracking_system.repository.TrackParcelRepository;
import com.project.tracking_system.service.analytics.DeliveryHistoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Тесты для {@link TrackDeletionService}.
 */
@ExtendWith(MockitoExtension.class)
class TrackDeletionServiceTest {

    @Mock
    private TrackParcelRepository trackParcelRepository;
    @Mock
    private DeliveryHistoryService deliveryHistoryService;

    private TrackDeletionService service;

    @BeforeEach
    void setUp() {
        service = new TrackDeletionService(trackParcelRepository, deliveryHistoryService);
    }

    /**
     * Проверяет, что сервис загружает посылки и корректно удаляет их.
     */
    @Test
    void deleteByNumbersAndUserId_DeletesParcelsAndClearsHistory() {
        List<String> numbers = List.of("T1", "T2");
        TrackParcel first = buildParcel("T1");
        TrackParcel second = buildParcel("T2");
        List<TrackParcel> parcels = List.of(first, second);
        when(trackParcelRepository.findByNumberInAndUserId(numbers, 1L)).thenReturn(parcels);

        service.deleteByNumbersAndUserId(numbers, 1L);

        verify(trackParcelRepository).findByNumberInAndUserId(numbers, 1L);
        verify(deliveryHistoryService).handleTrackParcelBeforeDelete(first);
        verify(deliveryHistoryService).handleTrackParcelBeforeDelete(second);
        assertNull(first.getDeliveryHistory());
        assertNull(second.getDeliveryHistory());
        verify(trackParcelRepository).deleteAll(parcels);
    }

    /**
     * Убеждается, что отсутствие посылок приводит к ошибке и удаление не выполняется.
     */
    @Test
    void deleteByNumbersAndUserId_NoParcelsFound_ThrowsException() {
        List<String> numbers = List.of("T1");
        when(trackParcelRepository.findByNumberInAndUserId(numbers, 2L)).thenReturn(List.of());

        assertThrows(RuntimeException.class, () -> service.deleteByNumbersAndUserId(numbers, 2L));
        verify(trackParcelRepository).findByNumberInAndUserId(numbers, 2L);
        verify(trackParcelRepository, never()).deleteAll(any());
    }

    /**
     * Проверяет удаление посылок по идентификаторам.
     */
    @Test
    void deleteByIdsAndUserId_DeletesParcels() {
        List<Long> ids = List.of(1L, 2L);
        TrackParcel first = buildParcel("T1");
        TrackParcel second = buildParcel("T2");
        List<TrackParcel> parcels = List.of(first, second);
        when(trackParcelRepository.findByIdInAndUserId(ids, 1L)).thenReturn(parcels);

        service.deleteByIdsAndUserId(ids, 1L);

        verify(trackParcelRepository).findByIdInAndUserId(ids, 1L);
        verify(deliveryHistoryService).handleTrackParcelBeforeDelete(first);
        verify(deliveryHistoryService).handleTrackParcelBeforeDelete(second);
        verify(trackParcelRepository).deleteAll(parcels);
    }

    /**
     * Проверяет, что отсутствие посылок по ID приводит к ошибке.
     */
    @Test
    void deleteByIdsAndUserId_NoParcelsFound_ThrowsException() {
        List<Long> ids = List.of(1L);
        when(trackParcelRepository.findByIdInAndUserId(ids, 2L)).thenReturn(List.of());

        assertThrows(RuntimeException.class, () -> service.deleteByIdsAndUserId(ids, 2L));
        verify(trackParcelRepository).findByIdInAndUserId(ids, 2L);
        verify(trackParcelRepository, never()).deleteAll(any());
    }

    /**
     * Создаёт тестовую посылку с историей доставки.
     */
    private static TrackParcel buildParcel(String number) {
        TrackParcel parcel = new TrackParcel();
        parcel.setNumber(number);
        parcel.setStatus(GlobalStatus.IN_TRANSIT);
        DeliveryHistory history = new DeliveryHistory();
        history.setTrackParcel(parcel);
        parcel.setDeliveryHistory(history);
        return parcel;
    }
}

