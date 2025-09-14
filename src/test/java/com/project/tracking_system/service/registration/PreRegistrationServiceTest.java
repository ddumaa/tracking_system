package com.project.tracking_system.service.registration;

import com.project.tracking_system.entity.Store;
import com.project.tracking_system.entity.TrackParcel;
import com.project.tracking_system.entity.User;
import com.project.tracking_system.entity.Customer;
import com.project.tracking_system.repository.TrackParcelRepository;
import com.project.tracking_system.repository.UserRepository;
import com.project.tracking_system.service.store.StoreService;
import com.project.tracking_system.service.customer.CustomerService;
import com.project.tracking_system.service.track.TrackTypeDetector;
import com.project.tracking_system.entity.PostalServiceType;
import org.springframework.web.server.ResponseStatusException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
    @Mock
    private CustomerService customerService;
    @Mock
    private TrackTypeDetector trackTypeDetector;

    private PreRegistrationService service;

    @BeforeEach
    void setUp() {
        service = new PreRegistrationService(trackParcelRepository, userRepository, storeService, customerService, trackTypeDetector);
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
        doNothing().when(customerService).updateStatsOnTrackAdd(any());
        when(trackTypeDetector.detect(any())).thenReturn(PostalServiceType.UNKNOWN);

        // Выполнение
        TrackParcel result = service.preRegister("   ", storeId, userId);

        // Проверка
        assertNull(result.getNumber(), "Трек-номер должен быть null при предрегистрации");
        ArgumentCaptor<TrackParcel> captor = ArgumentCaptor.forClass(TrackParcel.class);
        verify(trackParcelRepository).save(captor.capture());
        assertNull(captor.getValue().getNumber());
    }

    /**
     * При наличии телефона посылка связывается с покупателем.
     */
    @Test
    void preRegister_WithPhone_AssignsCustomer() {
        long storeId = 1L;
        long userId = 2L;
        String phone = "+375291234567";
        Store store = new Store();
        User user = new User();
        Customer customer = new Customer();

        when(storeService.getStore(storeId, userId)).thenReturn(store);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(customerService.registerOrGetByPhone(phone)).thenReturn(customer);
        when(trackParcelRepository.save(any(TrackParcel.class))).thenAnswer(inv -> inv.getArgument(0));
        doNothing().when(customerService).updateStatsOnTrackAdd(any());
        when(trackTypeDetector.detect(any())).thenReturn(PostalServiceType.BELPOST);

        TrackParcel result = service.preRegister(null, storeId, userId, phone);

        verify(customerService).registerOrGetByPhone(phone);
        verify(customerService).updateStatsOnTrackAdd(result);
        assertNull(result.getNumber());
        assertEquals(customer, result.getCustomer());
    }

    /**
     * Если сервис доставки не определён, выбрасывается исключение,
     * а запись не сохраняется.
     */
    @Test
    void preRegister_UnknownType_ThrowsException() {
        long storeId = 1L;
        long userId = 2L;
        String number = "XX123";

        when(storeService.getStore(storeId, userId)).thenReturn(new Store());
        when(userRepository.findById(userId)).thenReturn(Optional.of(new User()));
        when(trackTypeDetector.detect(any())).thenReturn(PostalServiceType.UNKNOWN);

        assertThrows(ResponseStatusException.class,
                () -> service.preRegister(number, storeId, userId));

        verify(trackParcelRepository, never()).save(any());
    }
}
