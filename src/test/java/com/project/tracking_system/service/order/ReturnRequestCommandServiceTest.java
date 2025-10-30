package com.project.tracking_system.service.order;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.tracking_system.controller.ReturnRequestCommandType;
import com.project.tracking_system.dto.ReturnRequestCommandRequest;
import com.project.tracking_system.dto.ReturnRequestDto;
import com.project.tracking_system.entity.OrderReturnRequest;
import com.project.tracking_system.entity.ReturnCommandLog;
import com.project.tracking_system.entity.TrackParcel;
import com.project.tracking_system.entity.User;
import com.project.tracking_system.repository.ReturnCommandLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.ZoneOffset;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Тесты сервиса идемпотентного выполнения команд возврата.
 */
@ExtendWith(MockitoExtension.class)
class ReturnRequestCommandServiceTest {

    @Mock
    private OrderReturnRequestService orderReturnRequestService;

    @Mock
    private ReturnRequestMapper returnRequestMapper;

    @Mock
    private ReturnCommandLogRepository returnCommandLogRepository;

    private ReturnRequestCommandService commandService;

    @BeforeEach
    void setUp() {
        commandService = new ReturnRequestCommandService(
                orderReturnRequestService,
                returnRequestMapper,
                returnCommandLogRepository,
                new ObjectMapper()
        );
    }

    @Test
    void executeCommand_whenRepeated_returnsStoredSnapshot() {
        User user = buildUser();
        OrderReturnRequest request = buildRequest(21L, 9L);
        OrderReturnRequest updated = buildRequest(21L, 9L);
        ReturnRequestDto dto = new ReturnRequestDto(21L, "EXCHANGE", null, null, null, false, null, null, null, null, null);
        ReturnRequestCommandRequest command = new ReturnRequestCommandRequest("dup-1", "start_exchange", null, null);

        when(orderReturnRequestService.getOwnedRequest(21L, user)).thenReturn(request);
        when(orderReturnRequestService.approveExchange(21L, 9L, user)).thenReturn(updated);
        when(returnRequestMapper.toDto(eq(updated), any())).thenReturn(dto);

        AtomicReference<ReturnCommandLog> savedLog = new AtomicReference<>();
        when(returnCommandLogRepository.findFirstByRequestIdAndIdempotencyKey(21L, "dup-1"))
                .thenAnswer(invocation -> Optional.ofNullable(savedLog.get()));
        when(returnCommandLogRepository.save(any(ReturnCommandLog.class)))
                .thenAnswer(invocation -> {
                    ReturnCommandLog logEntry = invocation.getArgument(0);
                    logEntry.setId(1L);
                    savedLog.set(logEntry);
                    return logEntry;
                });

        ReturnRequestDto first = commandService.executeCommand(21L,
                ReturnRequestCommandType.START_EXCHANGE,
                command,
                user,
                ZoneOffset.UTC);
        ReturnRequestDto second = commandService.executeCommand(21L,
                ReturnRequestCommandType.START_EXCHANGE,
                command,
                user,
                ZoneOffset.UTC);

        assertThat(first).isEqualTo(dto);
        assertThat(second).isEqualTo(dto);
        verify(orderReturnRequestService).approveExchange(21L, 9L, user);
        verify(returnRequestMapper).toDto(eq(updated), any());
        verify(returnCommandLogRepository).save(any(ReturnCommandLog.class));
    }

    @Test
    void executeCommand_whenPayloadDiffers_throwsConflict() {
        User user = buildUser();
        OrderReturnRequest request = buildRequest(21L, 9L);
        when(orderReturnRequestService.getOwnedRequest(21L, user)).thenReturn(request);

        ReturnCommandLog existing = new ReturnCommandLog();
        existing.setRequestId(21L);
        existing.setIdempotencyKey("dup-2");
        existing.setPayloadHash("conflict");
        existing.setResponseSnapshot("{}");

        when(returnCommandLogRepository.findFirstByRequestIdAndIdempotencyKey(21L, "dup-2"))
                .thenReturn(Optional.of(existing));

        ReturnRequestCommandRequest command = new ReturnRequestCommandRequest("dup-2", "start_exchange", null, null);

        assertThatThrownBy(() -> commandService.executeCommand(21L,
                ReturnRequestCommandType.START_EXCHANGE,
                command,
                user,
                ZoneOffset.UTC))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("уже выполнена");

        verify(orderReturnRequestService, never()).approveExchange(21L, 9L, user);
        verify(returnRequestMapper, never()).toDto(any(), any());
        verify(returnCommandLogRepository, never()).save(any(ReturnCommandLog.class));
    }

    private User buildUser() {
        User user = new User();
        user.setId(5L);
        return user;
    }

    private OrderReturnRequest buildRequest(Long requestId, Long parcelId) {
        OrderReturnRequest request = new OrderReturnRequest();
        request.setId(requestId);
        TrackParcel parcel = new TrackParcel();
        parcel.setId(parcelId);
        request.setParcel(parcel);
        return request;
    }
}
