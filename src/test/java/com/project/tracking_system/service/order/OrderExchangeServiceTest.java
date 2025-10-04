package com.project.tracking_system.service.order;

import com.project.tracking_system.entity.Customer;
import com.project.tracking_system.entity.GlobalStatus;
import com.project.tracking_system.entity.OrderReturnRequest;
import com.project.tracking_system.entity.Store;
import com.project.tracking_system.entity.TrackParcel;
import com.project.tracking_system.entity.User;
import com.project.tracking_system.repository.TrackParcelRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/**
 * Тесты сервиса {@link OrderExchangeService}.
 */
@ExtendWith(MockitoExtension.class)
class OrderExchangeServiceTest {

    @Mock
    private TrackParcelRepository trackParcelRepository;
    @Mock
    private OrderEpisodeLifecycleService episodeLifecycleService;

    private OrderExchangeService service;

    @BeforeEach
    void setUp() {
        service = new OrderExchangeService(trackParcelRepository, episodeLifecycleService);
    }

    @Test
    void createExchangeParcel_CopiesBaseAttributes() {
        Store store = new Store();
        store.setId(1L);
        Customer customer = new Customer();
        customer.setId(2L);
        User user = new User();
        user.setId(3L);

        TrackParcel original = new TrackParcel();
        original.setId(10L);
        original.setStore(store);
        original.setCustomer(customer);
        original.setUser(user);
        original.setStatus(GlobalStatus.DELIVERED);

        OrderReturnRequest request = new OrderReturnRequest();
        request.setId(20L);
        request.setParcel(original);

        ArgumentCaptor<TrackParcel> savedCaptor = ArgumentCaptor.forClass(TrackParcel.class);
        doAnswer(invocation -> {
            TrackParcel parcel = invocation.getArgument(0);
            parcel.setExchange(true);
            return null;
        }).when(episodeLifecycleService).registerExchange(any(TrackParcel.class), eq(original));
        when(trackParcelRepository.save(any(TrackParcel.class))).thenAnswer(invocation -> {
            TrackParcel parcel = invocation.getArgument(0);
            parcel.setId(99L);
            return parcel;
        });

        TrackParcel exchange = service.createExchangeParcel(request);

        verify(episodeLifecycleService).registerExchange(savedCaptor.capture(), eq(original));
        TrackParcel prepared = savedCaptor.getValue();
        assertThat(prepared.getStore()).isEqualTo(store);
        assertThat(prepared.getCustomer()).isEqualTo(customer);
        assertThat(prepared.getUser()).isEqualTo(user);
        assertThat(prepared.getStatus()).isEqualTo(GlobalStatus.PRE_REGISTERED);
        assertThat(exchange.getId()).isEqualTo(99L);
        assertThat(exchange.isExchange()).isTrue();
    }

    @Test
    void createExchangeParcel_ThrowsWhenRequestMissingParcel() {
        OrderReturnRequest request = new OrderReturnRequest();

        assertThatThrownBy(() -> service.createExchangeParcel(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("не содержит исходную посылку");
    }

    @Test
    void createExchangeParcel_ThrowsWhenRequestNull() {
        assertThatThrownBy(() -> service.createExchangeParcel(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Не передана");
    }

    @Test
    void cancelExchangeParcel_AssignsServiceTrackBeforeStatusChange() {
        TrackParcel original = new TrackParcel();
        original.setId(30L);

        TrackParcel replacement = new TrackParcel();
        replacement.setId(31L);
        replacement.setStatus(GlobalStatus.PRE_REGISTERED);
        replacement.setReplacementOf(original);

        OrderReturnRequest request = new OrderReturnRequest();
        request.setId(40L);
        request.setParcel(original);

        when(trackParcelRepository.findTopByReplacementOfOrderByTimestampDesc(original)).thenReturn(replacement);
        when(trackParcelRepository.save(any(TrackParcel.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(trackParcelRepository.findById(31L)).thenReturn(Optional.of(replacement));

        service.cancelExchangeParcel(request);

        assertThat(replacement.getStatus()).isEqualTo(GlobalStatus.REGISTRATION_CANCELLED);
        assertThat(replacement.isPreRegistered()).isFalse();
        assertThat(replacement.getNumber()).isNotBlank();
        assertThat(replacement.getNumber()).startsWith("SRV-");
        verify(trackParcelRepository).save(replacement);
    }

    @Test
    void findLatestExchangeAndEnsureTrackNotProvided_ReturnsParcelWhenTrackMissing() {
        TrackParcel original = new TrackParcel();
        original.setId(60L);

        TrackParcel replacement = new TrackParcel();
        replacement.setId(61L);
        replacement.setReplacementOf(original);

        OrderReturnRequest request = new OrderReturnRequest();
        request.setId(70L);
        request.setParcel(original);

        when(trackParcelRepository.findTopByReplacementOfOrderByTimestampDesc(original)).thenReturn(replacement);

        TrackParcel result = service.findLatestExchangeAndEnsureTrackNotProvided(request);

        assertThat(result).isSameAs(replacement);
    }

    @Test
    void findLatestExchangeAndEnsureTrackNotProvided_ThrowsWhenTrackPresent() {
        TrackParcel original = new TrackParcel();
        original.setId(80L);

        TrackParcel replacement = new TrackParcel();
        replacement.setId(81L);
        replacement.setReplacementOf(original);
        replacement.setNumber("AB123");

        OrderReturnRequest request = new OrderReturnRequest();
        request.setId(90L);
        request.setParcel(original);

        when(trackParcelRepository.findTopByReplacementOfOrderByTimestampDesc(original)).thenReturn(replacement);
        assertThatThrownBy(() -> service.findLatestExchangeAndEnsureTrackNotProvided(request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("магазин уже указал трек");
    }
}
