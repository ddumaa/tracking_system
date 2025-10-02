package com.project.tracking_system.service.order;

import com.project.tracking_system.entity.GlobalStatus;
import com.project.tracking_system.entity.OrderEpisode;
import com.project.tracking_system.entity.OrderReturnRequest;
import com.project.tracking_system.entity.OrderReturnRequestStatus;
import com.project.tracking_system.entity.TrackParcel;
import com.project.tracking_system.entity.User;
import com.project.tracking_system.repository.OrderReturnRequestRepository;
import com.project.tracking_system.service.track.TrackParcelService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/**
 * Тесты сервиса {@link OrderReturnRequestService}.
 */
@ExtendWith(MockitoExtension.class)
class OrderReturnRequestServiceTest {

    private static final String DEFAULT_REASON = "Размер не подошёл";
    private static final String DEFAULT_COMMENT = "Хочу выбрать другую модель";
    private static final ZonedDateTime DEFAULT_REQUESTED_AT = ZonedDateTime.of(
            2023, 5, 10, 12, 0, 0, 0, ZoneOffset.UTC
    );
    private static final String DEFAULT_REVERSE_TRACK = "BY1234567890";

    @Mock
    private OrderReturnRequestRepository repository;
    @Mock
    private TrackParcelService trackParcelService;
    @Mock
    private OrderEpisodeLifecycleService episodeLifecycleService;

    private OrderReturnRequestService service;

    private User user;

    @BeforeEach
    void setUp() {
        service = new OrderReturnRequestService(repository, trackParcelService, episodeLifecycleService);
        user = new User();
        user.setId(5L);
    }

    @Test
    void registerReturn_CreatesRequest_WhenDelivered() {
        TrackParcel parcel = buildParcel(10L, GlobalStatus.DELIVERED);
        when(trackParcelService.findOwnedById(10L, 5L)).thenReturn(Optional.of(parcel));
        when(repository.findByIdempotencyKey("key-1")).thenReturn(Optional.empty());
        when(repository.findFirstByParcel_IdAndStatusIn(eq(10L), any())).thenReturn(Optional.empty());
        OrderEpisode episode = parcel.getEpisode();
        when(episodeLifecycleService.ensureEpisode(parcel)).thenReturn(episode);
        when(repository.save(any(OrderReturnRequest.class))).thenAnswer(invocation -> {
            OrderReturnRequest request = invocation.getArgument(0);
            request.setId(100L);
            return request;
        });

        OrderReturnRequest saved = service.registerReturn(
                10L,
                user,
                "key-1",
                DEFAULT_REASON,
                DEFAULT_COMMENT,
                DEFAULT_REQUESTED_AT,
                DEFAULT_REVERSE_TRACK
        );

        assertThat(saved.getId()).isEqualTo(100L);
        assertThat(saved.getStatus()).isEqualTo(OrderReturnRequestStatus.REGISTERED);
        assertThat(saved.getCreatedBy()).isEqualTo(user);
        assertThat(saved.getCreatedAt()).isNotNull();

        ArgumentCaptor<OrderReturnRequest> captor = ArgumentCaptor.forClass(OrderReturnRequest.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getParcel()).isEqualTo(parcel);
        assertThat(captor.getValue().getReason()).isEqualTo(DEFAULT_REASON);
        assertThat(captor.getValue().getComment()).isEqualTo(DEFAULT_COMMENT);
        assertThat(captor.getValue().getRequestedAt()).isEqualTo(DEFAULT_REQUESTED_AT);
        assertThat(captor.getValue().getReverseTrackNumber()).isEqualTo(DEFAULT_REVERSE_TRACK);
    }

    @Test
    void registerReturn_ThrowsWhenStatusNotDelivered() {
        TrackParcel parcel = buildParcel(11L, GlobalStatus.IN_TRANSIT);
        when(trackParcelService.findOwnedById(11L, 5L)).thenReturn(Optional.of(parcel));
        when(repository.findByIdempotencyKey("key-2")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.registerReturn(
                11L,
                user,
                "key-2",
                DEFAULT_REASON,
                DEFAULT_COMMENT,
                DEFAULT_REQUESTED_AT,
                DEFAULT_REVERSE_TRACK
        ))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("доступна только для статуса");
    }

    @Test
    void approveExchange_ThrowsWhenEpisodeAlreadyHasApproved() {
        TrackParcel parcel = buildParcel(12L, GlobalStatus.DELIVERED);
        OrderReturnRequest request = new OrderReturnRequest();
        request.setId(200L);
        request.setParcel(parcel);
        request.setEpisode(parcel.getEpisode());
        request.setStatus(OrderReturnRequestStatus.REGISTERED);

        when(repository.findById(200L)).thenReturn(Optional.of(request));
        when(repository.existsByEpisode_IdAndStatus(parcel.getEpisode().getId(), OrderReturnRequestStatus.EXCHANGE_APPROVED))
                .thenReturn(true);

        assertThatThrownBy(() -> service.approveExchange(200L, 12L, user))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("уже запущен обмен");
    }

    @Test
    void closeWithoutExchange_ChangesStatusToClosed() {
        TrackParcel parcel = buildParcel(13L, GlobalStatus.DELIVERED);
        OrderReturnRequest request = new OrderReturnRequest();
        request.setId(300L);
        request.setParcel(parcel);
        request.setEpisode(parcel.getEpisode());
        request.setStatus(OrderReturnRequestStatus.REGISTERED);

        when(repository.findById(300L)).thenReturn(Optional.of(request));
        when(repository.save(any(OrderReturnRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));

        OrderReturnRequest result = service.closeWithoutExchange(300L, 13L, user);

        assertThat(result.getStatus()).isEqualTo(OrderReturnRequestStatus.CLOSED_NO_EXCHANGE);
        assertThat(result.getClosedBy()).isEqualTo(user);
        assertThat(result.getClosedAt()).isNotNull();
    }

    @Test
    void registerReturn_ReturnsExistingByIdempotencyKey() {
        TrackParcel parcel = buildParcel(14L, GlobalStatus.DELIVERED);
        OrderReturnRequest existing = new OrderReturnRequest();
        existing.setId(400L);
        existing.setParcel(parcel);
        existing.setEpisode(parcel.getEpisode());
        existing.setStatus(OrderReturnRequestStatus.REGISTERED);
        existing.setCreatedBy(user);

        when(repository.findByIdempotencyKey("same")).thenReturn(Optional.of(existing));

        existing.setReason(DEFAULT_REASON);
        existing.setComment(DEFAULT_COMMENT);
        existing.setRequestedAt(DEFAULT_REQUESTED_AT);
        existing.setReverseTrackNumber(DEFAULT_REVERSE_TRACK);

        OrderReturnRequest result = service.registerReturn(
                14L,
                user,
                "same",
                DEFAULT_REASON,
                DEFAULT_COMMENT,
                DEFAULT_REQUESTED_AT,
                DEFAULT_REVERSE_TRACK
        );

        assertThat(result).isEqualTo(existing);
        verify(repository, never()).save(any());
    }

    @Test
    void registerReturn_ThrowsWhenRequestBelongsToAnotherUser() {
        TrackParcel parcel = buildParcel(15L, GlobalStatus.DELIVERED);
        User another = new User();
        another.setId(9L);
        parcel.setUser(another);
        when(trackParcelService.findOwnedById(15L, 5L)).thenReturn(Optional.of(parcel));
        when(repository.findByIdempotencyKey("conflict")).thenReturn(Optional.empty());
        when(repository.findFirstByParcel_IdAndStatusIn(eq(15L), any())).thenReturn(Optional.empty());

        // emulate request saved earlier by other user
        OrderReturnRequest existing = new OrderReturnRequest();
        existing.setParcel(parcel);
        existing.setEpisode(parcel.getEpisode());
        existing.setStatus(OrderReturnRequestStatus.REGISTERED);
        existing.setCreatedBy(another);

        when(repository.findByIdempotencyKey("reuse")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.registerReturn(
                15L,
                user,
                "reuse",
                DEFAULT_REASON,
                DEFAULT_COMMENT,
                DEFAULT_REQUESTED_AT,
                DEFAULT_REVERSE_TRACK
        ))
                .isInstanceOf(AccessDeniedException.class);
    }

    private TrackParcel buildParcel(Long id, GlobalStatus status) {
        TrackParcel parcel = new TrackParcel();
        parcel.setId(id);
        parcel.setStatus(status);
        parcel.setLastUpdate(ZonedDateTime.now(ZoneOffset.UTC));
        parcel.setTimestamp(ZonedDateTime.now(ZoneOffset.UTC));
        OrderEpisode episode = new OrderEpisode();
        episode.setId(500L + id);
        parcel.setEpisode(episode);
        parcel.setUser(user);
        return parcel;
    }
}

