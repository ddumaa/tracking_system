package com.project.tracking_system.service.track;

import com.project.tracking_system.entity.TrackParcel;
import com.project.tracking_system.entity.User;
import com.project.tracking_system.exception.TrackNumberAlreadyExistsException;
import com.project.tracking_system.repository.TrackParcelRepository;
import com.project.tracking_system.repository.UserSubscriptionRepository;
import com.project.tracking_system.service.user.UserService;
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

    @BeforeEach
    void setUp() {
        service = new TrackParcelService(userService, trackParcelRepository, userSubscriptionRepository);
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
        when(trackParcelRepository.existsByNumberAndUserId(number, userId)).thenReturn(false);

        service.assignTrackNumber(parcelId, number, userId);

        verify(trackParcelRepository).updatePreRegisteredNumber(parcelId, number);
    }
}
