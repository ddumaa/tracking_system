package com.project.tracking_system.service.track;

import com.project.tracking_system.entity.TrackParcel;
import com.project.tracking_system.entity.User;
import com.project.tracking_system.exception.TrackNumberAlreadyExistsException;
import com.project.tracking_system.repository.TrackParcelRepository;
import com.project.tracking_system.repository.UserSubscriptionRepository;
import com.project.tracking_system.service.user.UserService;
import com.project.tracking_system.service.track.TrackServiceClassifier;
import com.project.tracking_system.entity.PostalServiceType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

/**
 * Тесты для метода {@link TrackParcelService#assignTrackNumber(Long, String, Long)}.
 */
@ExtendWith(MockitoExtension.class)
class TrackParcelServiceAssignNumberTest {

    @Mock
    private UserService userService;
    @Mock
    private TrackParcelRepository trackParcelRepository;
    @Mock
    private UserSubscriptionRepository userSubscriptionRepository;

    private TrackParcelService service;

    @Mock
    private TrackServiceClassifier trackServiceClassifier;

    @BeforeEach
    void setUp() {
        service = new TrackParcelService(userService, trackParcelRepository, userSubscriptionRepository, trackServiceClassifier);
    }

    /**
     * Проверяем, что при попытке присвоить уже существующий трек-номер
     * выбрасывается {@link TrackNumberAlreadyExistsException}.
     */
    @Test
    void assignTrackNumber_Duplicate_ThrowsException() {
        Long userId = 1L;
        Long parcelId = 2L;
        String number = "ABC";

        TrackParcel parcel = new TrackParcel();
        User user = new User();
        user.setId(userId);
        parcel.setUser(user);
        parcel.setPreRegistered(true);

        when(trackParcelRepository.findByIdAndPreRegisteredTrue(parcelId)).thenReturn(parcel);
        when(trackServiceClassifier.detect(number)).thenReturn(PostalServiceType.BELPOST);
        when(trackParcelRepository.existsByNumberAndUserId(number, userId)).thenReturn(true);

        assertThrows(TrackNumberAlreadyExistsException.class,
                () -> service.assignTrackNumber(parcelId, number, userId));
        verify(trackParcelRepository, never()).updatePreRegisteredNumber(anyLong(), anyString());
    }

    /**
     * Убеждаемся, что при отсутствии дубликата номер сохраняется.
     */
    @Test
    void assignTrackNumber_Success_UpdatesNumber() {
        Long userId = 1L;
        Long parcelId = 2L;
        String number = "ABC";

        TrackParcel parcel = new TrackParcel();
        User user = new User();
        user.setId(userId);
        parcel.setUser(user);
        parcel.setPreRegistered(true);

        when(trackParcelRepository.findByIdAndPreRegisteredTrue(parcelId)).thenReturn(parcel);
        when(trackServiceClassifier.detect(number)).thenReturn(PostalServiceType.BELPOST);
        when(trackParcelRepository.existsByNumberAndUserId(number, userId)).thenReturn(false);

        service.assignTrackNumber(parcelId, number, userId);

        verify(trackParcelRepository).updatePreRegisteredNumber(parcelId, number);

    }

    /**
     * Проверяем, что при неверном формате номера выбрасывается IllegalArgumentException.
     */
    @Test
    void assignTrackNumber_InvalidFormat_ThrowsIllegalArgumentException() {
        Long userId = 1L;
        Long parcelId = 2L;
        String number = "BAD";

        TrackParcel parcel = new TrackParcel();
        User user = new User();
        user.setId(userId);
        parcel.setUser(user);
        parcel.setPreRegistered(true);

        when(trackParcelRepository.findByIdAndPreRegisteredTrue(parcelId)).thenReturn(parcel);
        when(trackServiceClassifier.detect(number)).thenReturn(PostalServiceType.UNKNOWN);

        assertThrows(IllegalArgumentException.class,
                () -> service.assignTrackNumber(parcelId, number, userId));
        verify(trackParcelRepository, never()).updatePreRegisteredNumber(anyLong(), anyString());
    }
}
