package com.project.tracking_system.service.order;

import com.project.tracking_system.dto.ReturnRequestUpdateResponse;
import com.project.tracking_system.entity.Customer;
import com.project.tracking_system.entity.GlobalStatus;
import com.project.tracking_system.entity.OrderEpisode;
import com.project.tracking_system.entity.OrderReturnRequest;
import com.project.tracking_system.entity.OrderReturnRequestActionRequest;
import com.project.tracking_system.entity.OrderReturnRequestActionType;
import com.project.tracking_system.entity.OrderReturnRequestStatus;
import com.project.tracking_system.entity.TrackParcel;
import com.project.tracking_system.entity.User;
import com.project.tracking_system.repository.OrderReturnRequestActionRequestRepository;
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
import java.util.List;
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
    private static final boolean EXCHANGE_REQUESTED = true;
    private static final boolean NO_EXCHANGE_REQUESTED = false;

    @Mock
    private OrderReturnRequestRepository repository;
    @Mock
    private OrderReturnRequestActionRequestRepository actionRequestRepository;
    @Mock
    private TrackParcelService trackParcelService;
    @Mock
    private OrderEpisodeLifecycleService episodeLifecycleService;
    @Mock
    private OrderExchangeService orderExchangeService;

    private OrderReturnRequestService service;

    private User user;

    @BeforeEach
    void setUp() {
        service = new OrderReturnRequestService(repository, actionRequestRepository, trackParcelService,
                episodeLifecycleService, orderExchangeService);
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
                DEFAULT_REVERSE_TRACK,
                NO_EXCHANGE_REQUESTED
        );

        assertThat(saved.getId()).isEqualTo(100L);
        assertThat(saved.getStatus()).isEqualTo(OrderReturnRequestStatus.REGISTERED);
        assertThat(saved.getCreatedBy()).isEqualTo(user);
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.isExchangeRequested()).isFalse();

        ArgumentCaptor<OrderReturnRequest> captor = ArgumentCaptor.forClass(OrderReturnRequest.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getParcel()).isEqualTo(parcel);
        assertThat(captor.getValue().getReason()).isEqualTo(DEFAULT_REASON);
        assertThat(captor.getValue().getComment()).isEqualTo(DEFAULT_COMMENT);
        assertThat(captor.getValue().getRequestedAt()).isEqualTo(DEFAULT_REQUESTED_AT);
        assertThat(captor.getValue().getReverseTrackNumber()).isEqualTo(DEFAULT_REVERSE_TRACK);
        assertThat(captor.getValue().isExchangeRequested()).isFalse();
        verifyNoInteractions(orderExchangeService);
    }

    @Test
    void registerReturn_CreatesExchangeRequest_WhenFlagSet() {
        TrackParcel parcel = buildParcel(20L, GlobalStatus.DELIVERED);
        when(trackParcelService.findOwnedById(20L, 5L)).thenReturn(Optional.of(parcel));
        when(repository.findByIdempotencyKey("key-exchange")).thenReturn(Optional.empty());
        when(repository.findFirstByParcel_IdAndStatusIn(eq(20L), any())).thenReturn(Optional.empty());
        when(episodeLifecycleService.ensureEpisode(parcel)).thenReturn(parcel.getEpisode());
        when(repository.save(any(OrderReturnRequest.class))).thenAnswer(invocation -> {
            OrderReturnRequest request = invocation.getArgument(0);
            request.setId(101L);
            return request;
        });

        OrderReturnRequest saved = service.registerReturn(
                20L,
                user,
                "key-exchange",
                DEFAULT_REASON,
                DEFAULT_COMMENT,
                DEFAULT_REQUESTED_AT,
                DEFAULT_REVERSE_TRACK,
                EXCHANGE_REQUESTED
        );

        assertThat(saved.isExchangeRequested()).isTrue();
        assertThat(saved.getStatus()).isEqualTo(OrderReturnRequestStatus.REGISTERED);
        verify(repository).save(any(OrderReturnRequest.class));
        verifyNoInteractions(orderExchangeService);
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
                DEFAULT_REVERSE_TRACK,
                NO_EXCHANGE_REQUESTED
        ))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("доступна только для статуса");
    }

    @Test
    void registerReturn_ThrowsWhenExchangeFlagDiffersForIdempotentKey() {
        TrackParcel parcel = buildParcel(22L, GlobalStatus.DELIVERED);
        OrderReturnRequest existing = new OrderReturnRequest();
        existing.setParcel(parcel);
        existing.setEpisode(parcel.getEpisode());
        existing.setStatus(OrderReturnRequestStatus.REGISTERED);
        existing.setCreatedBy(user);
        existing.setReason(DEFAULT_REASON);
        existing.setComment(DEFAULT_COMMENT);
        existing.setRequestedAt(DEFAULT_REQUESTED_AT);
        existing.setReverseTrackNumber(DEFAULT_REVERSE_TRACK);
        existing.setExchangeRequested(true);

        when(repository.findByIdempotencyKey("dup")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.registerReturn(
                22L,
                user,
                "dup",
                DEFAULT_REASON,
                DEFAULT_COMMENT,
                DEFAULT_REQUESTED_AT,
                DEFAULT_REVERSE_TRACK,
                NO_EXCHANGE_REQUESTED
        ))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("другими данными");
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
    void approveExchange_CreatesExchangeParcel() {
        TrackParcel parcel = buildParcel(16L, GlobalStatus.DELIVERED);
        OrderReturnRequest request = new OrderReturnRequest();
        request.setId(500L);
        request.setParcel(parcel);
        request.setEpisode(parcel.getEpisode());
        request.setStatus(OrderReturnRequestStatus.REGISTERED);

        when(repository.findById(500L)).thenReturn(Optional.of(request));
        when(repository.existsByEpisode_IdAndStatus(parcel.getEpisode().getId(),
                OrderReturnRequestStatus.EXCHANGE_APPROVED)).thenReturn(false);
        when(repository.save(any(OrderReturnRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TrackParcel exchange = buildParcel(99L, GlobalStatus.PRE_REGISTERED);
        when(orderExchangeService.createExchangeParcel(any(OrderReturnRequest.class))).thenReturn(exchange);

        ExchangeApprovalResult result = service.approveExchange(500L, 16L, user);

        assertThat(result.request().getStatus()).isEqualTo(OrderReturnRequestStatus.EXCHANGE_APPROVED);
        assertThat(result.request().getDecisionBy()).isEqualTo(user);
        assertThat(result.request().getDecisionAt()).isNotNull();
        assertThat(result.request().isReturnReceiptConfirmed()).isTrue();
        assertThat(result.request().getReturnReceiptConfirmedAt()).isNotNull();
        assertThat(result.exchangeParcel()).isEqualTo(exchange);
        verify(orderExchangeService).createExchangeParcel(request);
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
        assertThat(result.isReturnReceiptConfirmed()).isTrue();
        assertThat(result.getReturnReceiptConfirmedAt()).isNotNull();
    }

    @Test
    void confirmReturnReceipt_SetsFlagWithoutClosing() {
        TrackParcel parcel = buildParcel(31L, GlobalStatus.DELIVERED);
        OrderReturnRequest request = new OrderReturnRequest();
        request.setId(901L);
        request.setParcel(parcel);
        request.setEpisode(parcel.getEpisode());
        request.setStatus(OrderReturnRequestStatus.REGISTERED);

        when(repository.findById(901L)).thenReturn(Optional.of(request));
        when(repository.save(any(OrderReturnRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));

        OrderReturnRequest result = service.confirmReturnReceipt(901L, 31L, user);

        assertThat(result.isReturnReceiptConfirmed()).isTrue();
        assertThat(result.getReturnReceiptConfirmedAt()).isNotNull();
        assertThat(result.getStatus()).isEqualTo(OrderReturnRequestStatus.REGISTERED);
    }

    @Test
    void confirmReturnReceipt_ReturnsExistingWhenAlreadyConfirmed() {
        TrackParcel parcel = buildParcel(32L, GlobalStatus.DELIVERED);
        OrderReturnRequest request = new OrderReturnRequest();
        request.setId(902L);
        request.setParcel(parcel);
        request.setEpisode(parcel.getEpisode());
        request.setStatus(OrderReturnRequestStatus.REGISTERED);
        request.setReturnReceiptConfirmed(true);
        request.setReturnReceiptConfirmedAt(ZonedDateTime.now(ZoneOffset.UTC).minusHours(1));

        when(repository.findById(902L)).thenReturn(Optional.of(request));

        OrderReturnRequest result = service.confirmReturnReceipt(902L, 32L, user);

        assertThat(result).isSameAs(request);
        verify(repository, never()).save(any());
    }

    @Test
    void confirmReturnReceipt_ThrowsWhenNotRegistered() {
        TrackParcel parcel = buildParcel(33L, GlobalStatus.DELIVERED);
        OrderReturnRequest request = new OrderReturnRequest();
        request.setId(903L);
        request.setParcel(parcel);
        request.setEpisode(parcel.getEpisode());
        request.setStatus(OrderReturnRequestStatus.EXCHANGE_APPROVED);

        when(repository.findById(903L)).thenReturn(Optional.of(request));

        assertThatThrownBy(() -> service.confirmReturnReceipt(903L, 33L, user))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("активной заявки");
    }

    @Test
    void cancelExchange_ClosesRequestWhenReplacementHasNoTrack() {
        TrackParcel parcel = buildParcel(19L, GlobalStatus.DELIVERED);
        TrackParcel replacement = new TrackParcel();
        replacement.setId(77L);
        OrderReturnRequest request = new OrderReturnRequest();
        request.setId(610L);
        request.setParcel(parcel);
        request.setEpisode(parcel.getEpisode());
        request.setStatus(OrderReturnRequestStatus.EXCHANGE_APPROVED);

        when(repository.findById(610L)).thenReturn(Optional.of(request));
        when(orderExchangeService.getLatestExchangeParcelOrThrowIfTracked(request))
                .thenReturn(Optional.of(replacement));
        when(repository.save(any(OrderReturnRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));

        OrderReturnRequest result = service.cancelExchange(610L, 19L, user);

        assertThat(result.getStatus()).isEqualTo(OrderReturnRequestStatus.CLOSED_NO_EXCHANGE);
        assertThat(result.getClosedBy()).isEqualTo(user);
        assertThat(result.getClosedAt()).isNotNull();
        assertThat(result.isReturnReceiptConfirmed()).isTrue();
        assertThat(result.getReturnReceiptConfirmedAt()).isNotNull();
        verify(orderExchangeService).cancelExchangeParcel(request, replacement);
        verify(episodeLifecycleService).decrementExchangeCount(parcel.getEpisode());
    }

    @Test
    void cancelExchange_ThrowsWhenTrackAlreadyAssigned() {
        TrackParcel parcel = buildParcel(21L, GlobalStatus.DELIVERED);
        OrderReturnRequest request = new OrderReturnRequest();
        request.setId(611L);
        request.setParcel(parcel);
        request.setEpisode(parcel.getEpisode());
        request.setStatus(OrderReturnRequestStatus.EXCHANGE_APPROVED);

        when(repository.findById(611L)).thenReturn(Optional.of(request));
        when(orderExchangeService.getLatestExchangeParcelOrThrowIfTracked(request))
                .thenThrow(new IllegalStateException("Отмена недоступна"));

        assertThatThrownBy(() -> service.cancelExchange(611L, 21L, user))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Отмена недоступна");
        verify(repository, never()).save(any());
        verify(orderExchangeService, never()).cancelExchangeParcel(any(), any());
        verify(episodeLifecycleService, never()).decrementExchangeCount(any());
    }

    @Test
    void canConfirmReceipt_ReturnsTrueOnlyForRegisteredAndNotConfirmed() {
        OrderReturnRequest request = new OrderReturnRequest();
        request.setStatus(OrderReturnRequestStatus.REGISTERED);
        assertThat(service.canConfirmReceipt(request)).isTrue();

        request.setReturnReceiptConfirmed(true);
        assertThat(service.canConfirmReceipt(request)).isFalse();

        request.setStatus(OrderReturnRequestStatus.EXCHANGE_APPROVED);
        request.setReturnReceiptConfirmed(false);
        assertThat(service.canConfirmReceipt(request)).isFalse();
    }

    @Test
    void requestMerchantAction_createsNewRequestWhenNotExists() {
        TrackParcel parcel = buildParcel(30L, GlobalStatus.DELIVERED);
        OrderReturnRequest request = buildExchangeRequest(700L, parcel);
        Customer customer = new Customer();
        customer.setId(55L);

        when(repository.findById(700L)).thenReturn(Optional.of(request));
        when(actionRequestRepository.findFirstByReturnRequest_IdAndActionAndProcessedAtIsNull(700L,
                OrderReturnRequestActionType.CANCEL_EXCHANGE)).thenReturn(Optional.empty());
        when(actionRequestRepository.save(any(OrderReturnRequestActionRequest.class))).thenAnswer(invocation -> {
            OrderReturnRequestActionRequest actionRequest = invocation.getArgument(0);
            actionRequest.setReturnRequest(request);
            actionRequest.setCustomer(customer);
            return actionRequest;
        });

        OrderReturnRequestActionRequest result = service.requestMerchantAction(
                700L,
                30L,
                user,
                customer,
                OrderReturnRequestActionType.CANCEL_EXCHANGE
        );

        assertThat(result).isNotNull();
        assertThat(result.getAction()).isEqualTo(OrderReturnRequestActionType.CANCEL_EXCHANGE);
        assertThat(result.getCustomer()).isEqualTo(customer);
        assertThat(result.getReturnRequest()).isEqualTo(request);
        verify(actionRequestRepository).save(any(OrderReturnRequestActionRequest.class));
    }

    @Test
    void requestMerchantAction_returnsExistingPendingRequest() {
        TrackParcel parcel = buildParcel(31L, GlobalStatus.DELIVERED);
        OrderReturnRequest request = buildExchangeRequest(701L, parcel);
        Customer customer = new Customer();
        customer.setId(56L);

        OrderReturnRequestActionRequest existing = new OrderReturnRequestActionRequest();
        existing.setReturnRequest(request);
        existing.setCustomer(customer);
        existing.setAction(OrderReturnRequestActionType.CONVERT_TO_RETURN);

        when(repository.findById(701L)).thenReturn(Optional.of(request));
        when(actionRequestRepository.findFirstByReturnRequest_IdAndActionAndProcessedAtIsNull(701L,
                OrderReturnRequestActionType.CONVERT_TO_RETURN)).thenReturn(Optional.of(existing));

        OrderReturnRequestActionRequest result = service.requestMerchantAction(
                701L,
                31L,
                user,
                customer,
                OrderReturnRequestActionType.CONVERT_TO_RETURN
        );

        assertThat(result).isSameAs(existing);
        verify(actionRequestRepository, never()).save(any());
    }

    @Test
    void findActiveRequestsWithDetails_ReturnsEmptyWhenUserIsNull() {
        List<OrderReturnRequest> result = service.findActiveRequestsWithDetails(null);

        assertThat(result).isEmpty();
        verify(repository, never()).findActiveRequestsWithDetails(anyLong(), any());
    }

    @Test
    void findActiveRequestsWithDetails_DelegatesToRepository() {
        OrderReturnRequest request = new OrderReturnRequest();
        when(repository.findActiveRequestsWithDetails(eq(5L), any())).thenReturn(List.of(request));

        List<OrderReturnRequest> result = service.findActiveRequestsWithDetails(5L);

        assertThat(result).containsExactly(request);
        verify(repository).findActiveRequestsWithDetails(eq(5L), any());
    }

    @Test
    void getExchangeCancellationBlockReason_ReturnsMessageWhenBlocked() {
        OrderReturnRequest request = new OrderReturnRequest();
        request.setStatus(OrderReturnRequestStatus.EXCHANGE_APPROVED);
        IllegalStateException cause = new IllegalStateException("Недоступно");
        when(orderExchangeService.getLatestExchangeParcelOrThrowIfTracked(request))
                .thenThrow(cause);

        Optional<String> reason = service.getExchangeCancellationBlockReason(request);

        assertThat(reason).contains("Недоступно");
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
        existing.setExchangeRequested(false);

        OrderReturnRequest result = service.registerReturn(
                14L,
                user,
                "same",
                DEFAULT_REASON,
                DEFAULT_COMMENT,
                DEFAULT_REQUESTED_AT,
                DEFAULT_REVERSE_TRACK,
                NO_EXCHANGE_REQUESTED
        );

        assertThat(result).isEqualTo(existing);
        verify(repository, never()).save(any());
        verifyNoInteractions(orderExchangeService);
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
                DEFAULT_REVERSE_TRACK,
                NO_EXCHANGE_REQUESTED
        ))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void updateReverseTrackAndComment_UpdatesFieldsForActiveRequest() {
        TrackParcel parcel = buildParcel(21L, GlobalStatus.DELIVERED);
        OrderReturnRequest request = new OrderReturnRequest();
        request.setId(801L);
        request.setParcel(parcel);
        request.setStatus(OrderReturnRequestStatus.REGISTERED);
        when(repository.findById(801L)).thenReturn(Optional.of(request));
        when(repository.save(any(OrderReturnRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ReturnRequestUpdateResponse response = service.updateReverseTrackAndComment(
                801L,
                21L,
                user,
                "  ab123  ",
                "  комментарий  "
        );

        assertThat(response.reverseTrackNumber()).isEqualTo("AB123");
        assertThat(response.comment()).isEqualTo("комментарий");
        assertThat(response.requestId()).isEqualTo(801L);
        verify(repository).save(any(OrderReturnRequest.class));
    }

    @Test
    void updateReverseTrackAndComment_ThrowsWhenStatusInactive() {
        TrackParcel parcel = buildParcel(22L, GlobalStatus.DELIVERED);
        OrderReturnRequest request = new OrderReturnRequest();
        request.setId(901L);
        request.setParcel(parcel);
        request.setStatus(OrderReturnRequestStatus.CLOSED_NO_EXCHANGE);
        when(repository.findById(901L)).thenReturn(Optional.of(request));

        assertThatThrownBy(() -> service.updateReverseTrackAndComment(
                901L,
                22L,
                user,
                "track",
                "comment"
        ))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("нельзя изменить");
        verify(repository, never()).save(any());
    }

    @Test
    void reopenAsReturn_ResetsExchangeAndSavesRequest() {
        TrackParcel parcel = buildParcel(23L, GlobalStatus.DELIVERED);
        OrderReturnRequest request = new OrderReturnRequest();
        request.setId(950L);
        request.setParcel(parcel);
        request.setEpisode(parcel.getEpisode());
        request.setStatus(OrderReturnRequestStatus.EXCHANGE_APPROVED);
        request.setDecisionBy(user);
        request.setDecisionAt(ZonedDateTime.now(ZoneOffset.UTC));
        request.setExchangeRequested(true);

        TrackParcel replacement = new TrackParcel();
        replacement.setId(81L);

        when(repository.findById(950L)).thenReturn(Optional.of(request));
        when(orderExchangeService.getLatestExchangeParcelOrThrowIfTracked(request))
                .thenReturn(Optional.of(replacement));
        when(repository.save(any(OrderReturnRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));

        OrderReturnRequest result = service.reopenAsReturn(950L, 23L, user);

        assertThat(result.getStatus()).isEqualTo(OrderReturnRequestStatus.REGISTERED);
        assertThat(result.getDecisionBy()).isNull();
        assertThat(result.getDecisionAt()).isNull();
        assertThat(result.getClosedBy()).isNull();
        assertThat(result.getClosedAt()).isNull();
        assertThat(result.isExchangeRequested()).isFalse();
        verify(orderExchangeService).cancelExchangeParcel(request, replacement);
        verify(episodeLifecycleService).decrementExchangeCount(parcel.getEpisode());
    }

    @Test
    void reopenAsReturn_ThrowsWhenTrackAlreadyAssigned() {
        TrackParcel parcel = buildParcel(24L, GlobalStatus.DELIVERED);
        OrderReturnRequest request = new OrderReturnRequest();
        request.setId(960L);
        request.setParcel(parcel);
        request.setEpisode(parcel.getEpisode());
        request.setStatus(OrderReturnRequestStatus.EXCHANGE_APPROVED);

        when(repository.findById(960L)).thenReturn(Optional.of(request));
        when(orderExchangeService.getLatestExchangeParcelOrThrowIfTracked(request))
                .thenThrow(new IllegalStateException("Магазин уже указал трек"));

        assertThatThrownBy(() -> service.reopenAsReturn(960L, 24L, user))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Магазин уже указал трек");
        verify(repository, never()).save(any());
        verify(orderExchangeService, never()).cancelExchangeParcel(any(), any());
        verify(episodeLifecycleService, never()).decrementExchangeCount(any());
    }

    private OrderReturnRequest buildExchangeRequest(Long id, TrackParcel parcel) {
        OrderReturnRequest request = new OrderReturnRequest();
        request.setId(id);
        request.setParcel(parcel);
        request.setEpisode(parcel.getEpisode());
        request.setStatus(OrderReturnRequestStatus.EXCHANGE_APPROVED);
        return request;
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

