package com.project.tracking_system.service.track;

import com.project.tracking_system.entity.DeliveryHistory;
import com.project.tracking_system.entity.GlobalStatus;
import com.project.tracking_system.entity.PostalServiceType;
import com.project.tracking_system.entity.Store;
import com.project.tracking_system.entity.TrackParcel;
import com.project.tracking_system.entity.User;
import com.project.tracking_system.exception.TrackNumberAlreadyExistsException;
import com.project.tracking_system.repository.DeliveryHistoryRepository;
import com.project.tracking_system.repository.TrackParcelRepository;
import com.project.tracking_system.repository.UserSubscriptionRepository;
import com.project.tracking_system.service.user.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.ZonedDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

/**
 * Тесты метода {@link TrackParcelService#updateTrackNumber(Long, Long, String)}.
 */
@ExtendWith(MockitoExtension.class)
class TrackParcelServiceUpdateNumberTest {

    @Mock
    private UserService userService;
    @Mock
    private TrackParcelRepository trackParcelRepository;
    @Mock
    private UserSubscriptionRepository userSubscriptionRepository;
    @Mock
    private TrackServiceClassifier trackServiceClassifier;
    @Mock
    private DeliveryHistoryRepository deliveryHistoryRepository;
    @Mock
    private TrackNumberAuditService trackNumberAuditService;

    private TrackParcelService service;

    @BeforeEach
    void setUp() {
        service = new TrackParcelService(
                userService,
                trackParcelRepository,
                userSubscriptionRepository,
                trackServiceClassifier,
                deliveryHistoryRepository,
                trackNumberAuditService
        );
    }

    /**
     * Успешное обновление номера сохраняет изменения и пишет аудит.
     */
    @Test
    void updateTrackNumber_Success_UpdatesEntities() {
        Long parcelId = 5L;
        Long userId = 10L;
        TrackParcel parcel = buildParcel(parcelId, userId, GlobalStatus.PRE_REGISTERED, "OLD123");
        DeliveryHistory history = new DeliveryHistory();
        history.setTrackParcel(parcel);
        history.setPostalService(PostalServiceType.BELPOST);

        when(trackParcelRepository.findByIdWithStoreAndUser(parcelId)).thenReturn(parcel);
        when(trackServiceClassifier.detect("NEW789")).thenReturn(PostalServiceType.CDEK);
        when(trackParcelRepository.existsByNumberAndUserId("NEW789", userId)).thenReturn(false);
        when(deliveryHistoryRepository.findByTrackParcelId(parcelId)).thenReturn(Optional.of(history));

        TrackParcel result = service.updateTrackNumber(parcelId, userId, "new789");

        assertThat(result.getNumber()).isEqualTo("NEW789");
        assertThat(result.getLastUpdate()).isNotNull();
        verify(trackParcelRepository).save(parcel);
        verify(deliveryHistoryRepository).save(history);
        verify(trackNumberAuditService).recordChange(parcel, "OLD123", "NEW789", userId);
    }

    /**
     * Попытка обновить номер в неподдерживаемом статусе выбрасывает исключение.
     */
    @Test
    void updateTrackNumber_StatusNotAllowed_ThrowsException() {
        Long parcelId = 7L;
        Long userId = 3L;
        TrackParcel parcel = buildParcel(parcelId, userId, GlobalStatus.IN_TRANSIT, "OLD");
        when(trackParcelRepository.findByIdWithStoreAndUser(parcelId)).thenReturn(parcel);

        assertThrows(IllegalStateException.class,
                () -> service.updateTrackNumber(parcelId, userId, "NEW"));
        verify(trackParcelRepository, never()).save(any());
        verify(trackNumberAuditService, never()).recordChange(any(), any(), any(), any());
    }

    /**
     * При попытке использовать занятый номер выбрасывается {@link TrackNumberAlreadyExistsException}.
     */
    @Test
    void updateTrackNumber_Duplicate_ThrowsConflict() {
        Long parcelId = 9L;
        Long userId = 4L;
        TrackParcel parcel = buildParcel(parcelId, userId, GlobalStatus.PRE_REGISTERED, "OLD");
        when(trackParcelRepository.findByIdWithStoreAndUser(parcelId)).thenReturn(parcel);
        when(trackServiceClassifier.detect("DUP"))
                .thenReturn(PostalServiceType.BELPOST);
        when(trackParcelRepository.existsByNumberAndUserId("DUP", userId)).thenReturn(true);

        assertThrows(TrackNumberAlreadyExistsException.class,
                () -> service.updateTrackNumber(parcelId, userId, "dup"));
        verify(trackParcelRepository, never()).save(any());
    }

    /**
     * Создаёт тестовую посылку с необходимыми полями.
     */
    private static TrackParcel buildParcel(Long parcelId,
                                           Long userId,
                                           GlobalStatus status,
                                           String number) {
        TrackParcel parcel = new TrackParcel();
        parcel.setId(parcelId);
        parcel.setNumber(number);
        parcel.setStatus(status);
        parcel.setLastUpdate(ZonedDateTime.now());
        Store store = new Store();
        store.setId(2L);
        parcel.setStore(store);
        User user = new User();
        user.setId(userId);
        parcel.setUser(user);
        return parcel;
    }
}
