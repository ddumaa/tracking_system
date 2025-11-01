package com.project.tracking_system.service.order;

import com.project.tracking_system.dto.ReturnRequestUpdateResponse;
import com.project.tracking_system.entity.Customer;
import com.project.tracking_system.entity.GlobalStatus;
import com.project.tracking_system.entity.OrderEpisode;
import com.project.tracking_system.entity.OrderReturnRequest;
import com.project.tracking_system.entity.OrderReturnRequestActionRequest;
import com.project.tracking_system.entity.ReturnRequestMode;
import com.project.tracking_system.entity.ReturnRequestStage;
import com.project.tracking_system.entity.Store;
import com.project.tracking_system.entity.ReturnRequestAction;
import com.project.tracking_system.entity.OrderReturnRequestStatus;
import com.project.tracking_system.entity.TrackParcel;
import com.project.tracking_system.entity.User;
import com.project.tracking_system.repository.OrderReturnRequestActionRequestRepository;
import com.project.tracking_system.repository.OrderReturnRequestRepository;
import com.project.tracking_system.service.track.TrackParcelService;
import com.project.tracking_system.service.track.TrackViewCacheInvalidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.EnumSet;
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
    @Mock
    private TrackViewCacheInvalidator trackViewCacheInvalidator;

    private OrderReturnRequestService service;

    private User user;
    private ReturnRequestWorkflow workflow;

    @BeforeEach
    void setUp() {
        workflow = new ReturnRequestWorkflow();
        service = new OrderReturnRequestService(repository, actionRequestRepository, trackParcelService,
                episodeLifecycleService, orderExchangeService, trackViewCacheInvalidator, workflow);
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
        assertThat(saved.getMode()).isEqualTo(ReturnRequestMode.RETURN);
        assertThat(saved.getStage()).isEqualTo(ReturnRequestStage.NEW);
        assertThat(saved.getStore()).isEqualTo(parcel.getStore());
        assertThat(saved.getResponsibleManager()).isEqualTo(user);
        assertThat(saved.isManualTrackOverride()).isTrue();
        assertThat(saved.isManualStageOverride()).isFalse();
        assertThat(saved.getStageStartedAt()).isEqualTo(DEFAULT_REQUESTED_AT);
        assertThat(saved.getStageUpdatedAt()).isEqualTo(DEFAULT_REQUESTED_AT);
        assertThat(saved.getHistoryEntries()).hasSize(1);

        ArgumentCaptor<OrderReturnRequest> captor = ArgumentCaptor.forClass(OrderReturnRequest.class);
        verify(repository).save(captor.capture());
        OrderReturnRequest persisted = captor.getValue();
        assertThat(persisted.getParcel()).isEqualTo(parcel);
        assertThat(persisted.getReason()).isEqualTo(DEFAULT_REASON);
        assertThat(persisted.getComment()).isEqualTo(DEFAULT_COMMENT);
        assertThat(persisted.getRequestedAt()).isEqualTo(DEFAULT_REQUESTED_AT);
        assertThat(persisted.getReverseTrackNumber()).isEqualTo(DEFAULT_REVERSE_TRACK);
        assertThat(persisted.isExchangeRequested()).isFalse();
        assertThat(persisted.getHistoryEntries()).hasSize(1);
        verifyNoInteractions(orderExchangeService);
        verify(trackViewCacheInvalidator).evictTrackDetails(user.getId(), parcel.getId());
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
        assertThat(saved.getMode()).isEqualTo(ReturnRequestMode.EXCHANGE);
        assertThat(saved.getStage()).isEqualTo(ReturnRequestStage.NEW);
        assertThat(saved.getStore()).isEqualTo(parcel.getStore());
        assertThat(saved.getHistoryEntries()).hasSize(1);
        verify(repository).save(any(OrderReturnRequest.class));
        verifyNoInteractions(orderExchangeService);
        verify(trackViewCacheInvalidator).evictTrackDetails(user.getId(), parcel.getId());
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
    void approveExchange_ChangesStatusWithoutCreatingParcel() {
        TrackParcel parcel = buildParcel(16L, GlobalStatus.DELIVERED);
        OrderReturnRequest request = new OrderReturnRequest();
        request.setId(500L);
        request.setParcel(parcel);
        request.setEpisode(parcel.getEpisode());
        request.setStore(parcel.getStore());
        request.setStatus(OrderReturnRequestStatus.REGISTERED);

        when(repository.findById(500L)).thenReturn(Optional.of(request));
        when(repository.existsByEpisode_IdAndStatus(parcel.getEpisode().getId(),
                OrderReturnRequestStatus.EXCHANGE_APPROVED)).thenReturn(false);
        when(repository.save(any(OrderReturnRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));

        OrderReturnRequest result = service.approveExchange(500L, 16L, user);

        assertThat(result.getStatus()).isEqualTo(OrderReturnRequestStatus.EXCHANGE_APPROVED);
        assertThat(result.getDecisionBy()).isEqualTo(user);
        assertThat(result.getDecisionAt()).isNotNull();
        assertThat(result.getMode()).isEqualTo(ReturnRequestMode.EXCHANGE);
        assertThat(result.getStage()).isEqualTo(ReturnRequestStage.EXCHANGE_REGISTERED);
        assertThat(result.getResponsibleManager()).isEqualTo(user);
        assertThat(result.isManualStageOverride()).isTrue();
        verify(orderExchangeService, never()).createExchangeParcel(any());
        verify(trackViewCacheInvalidator).evictTrackDetails(user.getId(), parcel.getId());
    }

    @Test
    void createExchangeParcel_CreatesParcelWhenAllowed() {
        TrackParcel parcel = buildParcel(44L, GlobalStatus.DELIVERED);
        OrderReturnRequest request = new OrderReturnRequest();
        request.setId(701L);
        request.setParcel(parcel);
        request.setEpisode(parcel.getEpisode());
        request.setStore(parcel.getStore());
        request.setStatus(OrderReturnRequestStatus.EXCHANGE_APPROVED);

        when(repository.findById(701L)).thenReturn(Optional.of(request));
        when(orderExchangeService.findLatestExchangeParcel(request)).thenReturn(Optional.empty());
        when(repository.save(any(OrderReturnRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TrackParcel exchange = buildParcel(199L, GlobalStatus.PRE_REGISTERED);
        when(orderExchangeService.createExchangeParcel(request)).thenReturn(exchange);

        TrackParcel created = service.createExchangeParcel(701L, 44L, user);

        assertThat(created).isEqualTo(exchange);
        assertThat(request.getResponsibleManager()).isEqualTo(user);
        assertThat(request.isManualStageOverride()).isTrue();
        assertThat(request.getStage()).isEqualTo(ReturnRequestStage.EXCHANGE_SENT);
        assertThat(request.getHistoryEntries()).isNotEmpty();
        verify(orderExchangeService).createExchangeParcel(request);
        verify(trackViewCacheInvalidator).evictTrackDetails(user.getId(), parcel.getId());
    }

    @Test
    void createExchangeParcel_ThrowsWhenActiveExists() {
        TrackParcel parcel = buildParcel(45L, GlobalStatus.DELIVERED);
        OrderReturnRequest request = new OrderReturnRequest();
        request.setId(702L);
        request.setParcel(parcel);
        request.setEpisode(parcel.getEpisode());
        request.setStatus(OrderReturnRequestStatus.EXCHANGE_APPROVED);

        TrackParcel activeReplacement = buildParcel(300L, GlobalStatus.PRE_REGISTERED);

        when(repository.findById(702L)).thenReturn(Optional.of(request));
        when(orderExchangeService.findLatestExchangeParcel(request)).thenReturn(Optional.of(activeReplacement));

        assertThatThrownBy(() -> service.createExchangeParcel(702L, 45L, user))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("уже создана");
        verify(orderExchangeService, never()).createExchangeParcel(any());
    }

    @Test
    void closeWithoutExchange_ChangesStatusToClosed() {
        TrackParcel parcel = buildParcel(13L, GlobalStatus.DELIVERED);
        OrderReturnRequest request = new OrderReturnRequest();
        request.setId(300L);
        request.setParcel(parcel);
        request.setEpisode(parcel.getEpisode());
        request.setStore(parcel.getStore());
        request.setStatus(OrderReturnRequestStatus.REGISTERED);

        when(repository.findById(300L)).thenReturn(Optional.of(request));
        when(repository.save(any(OrderReturnRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));

        OrderReturnRequest result = service.closeWithoutExchange(300L, 13L, user);

        assertThat(result.getStatus()).isEqualTo(OrderReturnRequestStatus.CLOSED_NO_EXCHANGE);
        assertThat(result.getClosedBy()).isEqualTo(user);
        assertThat(result.getClosedAt()).isNotNull();
        assertThat(result.getMode()).isEqualTo(ReturnRequestMode.RETURN);
        assertThat(result.getStage()).isEqualTo(ReturnRequestStage.INBOUND_PICKED_UP);
        assertThat(result.getResponsibleManager()).isEqualTo(user);
        assertThat(result.isManualStageOverride()).isTrue();
        assertThat(result.getHistoryEntries()).isNotEmpty();
        verify(trackViewCacheInvalidator).evictTrackDetails(user.getId(), parcel.getId());
    }

    @Test
    void confirmReturnProcessing_SetsFlagWithoutClosing() {
        TrackParcel parcel = buildParcel(31L, GlobalStatus.DELIVERED);
        OrderReturnRequest request = new OrderReturnRequest();
        request.setId(901L);
        request.setParcel(parcel);
        request.setEpisode(parcel.getEpisode());
        request.setStore(parcel.getStore());
        request.setStatus(OrderReturnRequestStatus.REGISTERED);

        when(repository.findById(901L)).thenReturn(Optional.of(request));
        when(repository.save(any(OrderReturnRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));

        OrderReturnRequest result = service.confirmReturnProcessing(901L, 31L, user);

        assertThat(result.isReturnReceiptConfirmed()).isTrue();
        assertThat(result.getReturnReceiptConfirmedAt()).isNotNull();
        assertThat(result.getStatus()).isEqualTo(OrderReturnRequestStatus.REGISTERED);
        assertThat(result.getStage()).isEqualTo(ReturnRequestStage.INBOUND_PICKED_UP);
        assertThat(result.getResponsibleManager()).isEqualTo(user);
        assertThat(result.isManualStageOverride()).isTrue();
        assertThat(result.getHistoryEntries()).isNotEmpty();
        verify(trackViewCacheInvalidator).evictTrackDetails(user.getId(), parcel.getId());
    }

    @Test
    void confirmReturnProcessing_ReturnsExistingWhenAlreadyConfirmed() {
        TrackParcel parcel = buildParcel(32L, GlobalStatus.DELIVERED);
        OrderReturnRequest request = new OrderReturnRequest();
        request.setId(902L);
        request.setParcel(parcel);
        request.setEpisode(parcel.getEpisode());
        request.setStatus(OrderReturnRequestStatus.REGISTERED);
        request.setReturnReceiptConfirmed(true);
        request.setReturnReceiptConfirmedAt(ZonedDateTime.now(ZoneOffset.UTC).minusHours(1));

        when(repository.findById(902L)).thenReturn(Optional.of(request));

        OrderReturnRequest result = service.confirmReturnProcessing(902L, 32L, user);

        assertThat(result).isSameAs(request);
        verify(repository, never()).save(any());
    }

    @Test
    void confirmReturnProcessing_ThrowsWhenExchangeApproved() {
        TrackParcel parcel = buildParcel(33L, GlobalStatus.DELIVERED);
        OrderReturnRequest request = new OrderReturnRequest();
        request.setId(903L);
        request.setParcel(parcel);
        request.setEpisode(parcel.getEpisode());
        request.setStatus(OrderReturnRequestStatus.EXCHANGE_APPROVED);

        when(repository.findById(903L)).thenReturn(Optional.of(request));

        assertThatThrownBy(() -> service.confirmReturnProcessing(903L, 33L, user))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("активной заявки или закрытия без обмена");
    }

    @Test
    void confirmReturnProcessing_AllowsClosedRequest() {
        TrackParcel parcel = buildParcel(34L, GlobalStatus.DELIVERED);
        OrderReturnRequest request = new OrderReturnRequest();
        request.setId(904L);
        request.setParcel(parcel);
        request.setEpisode(parcel.getEpisode());
        request.setStore(parcel.getStore());
        request.setStatus(OrderReturnRequestStatus.CLOSED_NO_EXCHANGE);
        request.setClosedBy(user);
        request.setClosedAt(ZonedDateTime.now(ZoneOffset.UTC).minusHours(1));

        when(repository.findById(904L)).thenReturn(Optional.of(request));
        when(repository.save(any(OrderReturnRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));

        OrderReturnRequest result = service.confirmReturnProcessing(904L, 34L, user);

        assertThat(result.isReturnReceiptConfirmed()).isTrue();
        assertThat(result.getReturnReceiptConfirmedAt()).isNotNull();
        assertThat(result.getStatus()).isEqualTo(OrderReturnRequestStatus.CLOSED_NO_EXCHANGE);
        assertThat(result.getStage()).isEqualTo(ReturnRequestStage.INBOUND_PICKED_UP);
        verify(trackViewCacheInvalidator).evictTrackDetails(user.getId(), parcel.getId());
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
        request.setStore(parcel.getStore());
        request.setStatus(OrderReturnRequestStatus.EXCHANGE_APPROVED);

        when(repository.findById(610L)).thenReturn(Optional.of(request));
        when(orderExchangeService.getLatestExchangeParcelOrThrowIfTracked(request))
                .thenReturn(Optional.of(replacement));
        when(repository.save(any(OrderReturnRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));

        OrderReturnRequest result = service.cancelExchange(610L, 19L, user);

        assertThat(result.getStatus()).isEqualTo(OrderReturnRequestStatus.CLOSED_NO_EXCHANGE);
        assertThat(result.getClosedBy()).isEqualTo(user);
        assertThat(result.getClosedAt()).isNotNull();
        assertThat(result.getMode()).isEqualTo(ReturnRequestMode.RETURN);
        assertThat(result.getStage()).isEqualTo(ReturnRequestStage.INBOUND_PICKED_UP);
        assertThat(result.getHistoryEntries()).isNotEmpty();
        verify(orderExchangeService).cancelExchangeParcel(request, replacement);
        verify(episodeLifecycleService).decrementExchangeCount(parcel.getEpisode());
        verify(trackViewCacheInvalidator).evictTrackDetails(user.getId(), parcel.getId());
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
    void canConfirmReceipt_ReturnsTrueForActiveOrClosedWithoutConfirmation() {
        OrderReturnRequest request = new OrderReturnRequest();
        request.setStatus(OrderReturnRequestStatus.REGISTERED);
        assertThat(service.canConfirmReceipt(request)).isTrue();

        request.setReturnReceiptConfirmed(true);
        assertThat(service.canConfirmReceipt(request)).isFalse();

        request.setReturnReceiptConfirmed(false);
        request.setStatus(OrderReturnRequestStatus.CLOSED_NO_EXCHANGE);
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
                ReturnRequestAction.CANCEL_EXCHANGE)).thenReturn(Optional.empty());
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
                ReturnRequestAction.CANCEL_EXCHANGE
        );

        assertThat(result).isNotNull();
        assertThat(result.getAction()).isEqualTo(ReturnRequestAction.CANCEL_EXCHANGE);
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
        existing.setAction(ReturnRequestAction.REOPEN_RETURN);

        when(repository.findById(701L)).thenReturn(Optional.of(request));
        when(actionRequestRepository.findFirstByReturnRequest_IdAndActionAndProcessedAtIsNull(701L,
                ReturnRequestAction.REOPEN_RETURN)).thenReturn(Optional.of(existing));

        OrderReturnRequestActionRequest result = service.requestMerchantAction(
                701L,
                31L,
                user,
                customer,
                ReturnRequestAction.REOPEN_RETURN
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
        assertThat(request.isManualTrackOverride()).isTrue();
        assertThat(request.getHistoryEntries()).hasSize(1);
        assertThat(request.getResponsibleManager()).isEqualTo(user);
        verify(repository).save(any(OrderReturnRequest.class));
        verify(trackViewCacheInvalidator).evictTrackDetails(user.getId(), parcel.getId());
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
        request.setStore(parcel.getStore());
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
        assertThat(result.getMode()).isEqualTo(ReturnRequestMode.RETURN);
        assertThat(result.getStage()).isEqualTo(ReturnRequestStage.INBOUND_PICKED_UP);
        assertThat(result.getHistoryEntries()).isNotEmpty();
        verify(orderExchangeService).cancelExchangeParcel(request, replacement);
        verify(episodeLifecycleService).decrementExchangeCount(parcel.getEpisode());
        verify(trackViewCacheInvalidator).evictTrackDetails(user.getId(), parcel.getId());
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

    @Test
    void resolveAvailableActions_ReturnsCancelForRegisteredRequest() {
        OrderReturnRequest request = new OrderReturnRequest();
        request.setStatus(OrderReturnRequestStatus.REGISTERED);
        request.setStage(ReturnRequestStage.NEW);
        request.setMode(ReturnRequestMode.RETURN);

        EnumSet<ReturnRequestAction> actions = service.resolveAvailableActions(request);

        assertThat(actions).contains(ReturnRequestAction.CLOSE, ReturnRequestAction.START_EXCHANGE);
        assertThat(actions).doesNotContain(ReturnRequestAction.CANCEL_EXCHANGE, ReturnRequestAction.REOPEN_RETURN);
    }

    @Test
    void resolveAvailableActions_ExcludesExchangeActionsWhenShipmentDispatched() {
        OrderReturnRequest request = new OrderReturnRequest();
        request.setStatus(OrderReturnRequestStatus.EXCHANGE_APPROVED);
        request.setStage(ReturnRequestStage.EXCHANGE_SENT);
        request.setMode(ReturnRequestMode.EXCHANGE);

        TrackParcel replacement = new TrackParcel();
        replacement.setNumber("TRK123");
        replacement.setStatus(GlobalStatus.IN_TRANSIT);

        when(orderExchangeService.findLatestExchangeParcel(request)).thenReturn(Optional.of(replacement));
        when(orderExchangeService.getLatestExchangeParcelOrThrowIfTracked(request))
                .thenThrow(new IllegalStateException("Отмена недоступна"));

        EnumSet<ReturnRequestAction> actions = service.resolveAvailableActions(request);

        assertThat(actions).doesNotContain(ReturnRequestAction.CANCEL_EXCHANGE, ReturnRequestAction.REOPEN_RETURN);
    }

    private OrderReturnRequest buildExchangeRequest(Long id, TrackParcel parcel) {
        OrderReturnRequest request = new OrderReturnRequest();
        request.setId(id);
        request.setParcel(parcel);
        request.setEpisode(parcel.getEpisode());
        request.setStore(parcel.getStore());
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
        Store store = new Store();
        store.setId(900L + id);
        store.setName("Store-" + id);
        parcel.setStore(store);
        parcel.setUser(user);
        return parcel;
    }
}

