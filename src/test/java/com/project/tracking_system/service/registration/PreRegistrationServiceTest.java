package com.project.tracking_system.service.registration;

import com.project.tracking_system.entity.Store;
import com.project.tracking_system.entity.TrackParcel;
import com.project.tracking_system.entity.User;
import com.project.tracking_system.repository.TrackParcelRepository;
import com.project.tracking_system.repository.UserRepository;
import com.project.tracking_system.service.store.StoreService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Тесты для {@link PreRegistrationService}.
 * Проверяется, что при пустом трек‑номере
 * в базу сохраняется {@code null}.
 */
@ExtendWith(MockitoExtension.class)
class PreRegistrationServiceTest {

    @Mock
    private TrackParcelRepository trackParcelRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private StoreService storeService;

    private PreRegistrationService service;

    @BeforeEach
    void setUp() {
        service = new PreRegistrationService(trackParcelRepository, userRepository, storeService);
    }

    /**
     * Убеждаемся, что пустой номер не пытается сохраниться как строка
     * и остаётся {@code null}.
     */
    @Test
    void preRegister_BlankNumber_StoredAsNull() {
        // Подготовка данных
        long storeId = 1L;
        long userId = 2L;
        when(storeService.getStore(storeId, userId)).thenReturn(new Store());
        when(userRepository.findById(userId)).thenReturn(Optional.of(new User()));
        when(trackParcelRepository.save(any(TrackParcel.class))).thenAnswer(inv -> inv.getArgument(0));

        // Выполнение
        TrackParcel result = service.preRegister("   ", storeId, userId);

        // Проверка
        assertNull(result.getNumber(), "Трек-номер должен быть null при предрегистрации");
        ArgumentCaptor<TrackParcel> captor = ArgumentCaptor.forClass(TrackParcel.class);
        verify(trackParcelRepository).save(captor.capture());
        assertNull(captor.getValue().getNumber());
    }
}
