package com.project.tracking_system.service.order;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.tracking_system.controller.ReturnRequestCommandType;
import com.project.tracking_system.dto.CommandDto;
import com.project.tracking_system.dto.RequestDto;
import com.project.tracking_system.dto.ReturnRequestTimestampsDto;
import com.project.tracking_system.entity.OrderReturnRequest;
import com.project.tracking_system.entity.ReturnCommandLog;
import com.project.tracking_system.entity.TrackParcel;
import com.project.tracking_system.entity.User;
import com.project.tracking_system.service.order.ReturnRequestMapper;
import com.project.tracking_system.repository.ReturnCommandLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
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
        RequestDto dto = buildRequestDto(21L, "EXCHANGE");
        CommandDto command = new CommandDto("dup-1", "start_exchange", null);

        when(orderReturnRequestService.getOwnedRequest(21L, user)).thenReturn(request);
        when(orderReturnRequestService.approveExchange(21L, 9L, user)).thenReturn(updated);
        when(returnRequestMapper.toDto(eq(updated), any())).thenReturn(dto);

        AtomicReference<ReturnCommandLog> savedLog = new AtomicReference<>();
        when(returnCommandLogRepository.findFirstByRequestIdAndIdempotencyKey(21L, "dup-1"))
                .thenAnswer(invocation -> Optional.ofNullable(savedLog.get()));
        when(returnCommandLogRepository.saveAndFlush(any(ReturnCommandLog.class)))
                .thenAnswer(invocation -> {
                    ReturnCommandLog logEntry = invocation.getArgument(0);
                    logEntry.setId(1L);
                    savedLog.set(logEntry);
                    return logEntry;
                });

        RequestDto first = commandService.executeCommand(21L,
                ReturnRequestCommandType.START_EXCHANGE,
                command,
                user,
                ZoneOffset.UTC);
        RequestDto second = commandService.executeCommand(21L,
                ReturnRequestCommandType.START_EXCHANGE,
                command,
                user,
                ZoneOffset.UTC);

        assertThat(first).isEqualTo(dto);
        assertThat(second).isEqualTo(dto);
        verify(orderReturnRequestService).approveExchange(21L, 9L, user);
        verify(returnRequestMapper).toDto(eq(updated), any());
        verify(returnCommandLogRepository, atLeast(2)).saveAndFlush(any(ReturnCommandLog.class));
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

        CommandDto command = new CommandDto("dup-2", "start_exchange", null);

        assertThatThrownBy(() -> commandService.executeCommand(21L,
                ReturnRequestCommandType.START_EXCHANGE,
                command,
                user,
                ZoneOffset.UTC))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("уже выполнена");

        verify(orderReturnRequestService, never()).approveExchange(21L, 9L, user);
        verify(returnRequestMapper, never()).toDto(any(), any());
        verify(returnCommandLogRepository, never()).saveAndFlush(any(ReturnCommandLog.class));
    }

    @Test
    void executeCommand_whenConcurrentReservation_returnsStoredSnapshot() {
        User user = buildUser();
        OrderReturnRequest request = buildRequest(22L, 10L);
        CommandDto command = new CommandDto("dup-3", "start_exchange", null);

        RequestDto storedDto = buildRequestDto(22L, "EXCHANGE");
        String snapshot;
        try {
            snapshot = new ObjectMapper().writeValueAsString(storedDto);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }

        when(orderReturnRequestService.getOwnedRequest(22L, user)).thenReturn(request);
        ReturnCommandLog completed = new ReturnCommandLog();
        completed.setRequestId(22L);
        completed.setIdempotencyKey("dup-3");
        completed.setPayloadHash(computePayloadHash(ReturnRequestCommandType.START_EXCHANGE, command));
        completed.setResponseSnapshot(snapshot);

        when(returnCommandLogRepository.findFirstByRequestIdAndIdempotencyKey(22L, "dup-3"))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(completed));
        when(returnCommandLogRepository.saveAndFlush(any(ReturnCommandLog.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate"));

        RequestDto result = commandService.executeCommand(22L,
                ReturnRequestCommandType.START_EXCHANGE,
                command,
                user,
                ZoneOffset.UTC);

        assertThat(result.id()).isEqualTo(22L);
        verify(orderReturnRequestService, never()).approveExchange(any(), any(), any());
        verify(returnRequestMapper, never()).toDto(any(), any());
    }

    private String computePayloadHash(ReturnRequestCommandType type, CommandDto command) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(type.name().getBytes(StandardCharsets.UTF_8));
            digest.update(nullSafeBytes(command.action()));
            CommandDto.Payload payload = command.payload();
            if (payload != null) {
                digest.update(nullSafeBytes(payload.reverseTrack()));
                digest.update(nullSafeBytes(payload.comment()));
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private byte[] nullSafeBytes(String value) {
        return value != null ? value.getBytes(StandardCharsets.UTF_8) : new byte[0];
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

    private RequestDto buildRequestDto(Long id, String status) {
        return new RequestDto(
                id,
                status,
                status,
                null,
                null,
                "RETURN",
                "NEW",
                false,
                null,
                null,
                false,
                false,
                false,
                false,
                false,
                false,
                List.of(),
                new ReturnRequestTimestampsDto(null, null, null, null, null, null, null, null),
                null,
                null
        );
    }
}
