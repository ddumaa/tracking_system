package com.project.tracking_system.service.order;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.project.tracking_system.controller.ReturnRequestCommandType;
import com.project.tracking_system.dto.CommandDto;
import com.project.tracking_system.dto.RequestDto;
import com.project.tracking_system.entity.OrderReturnRequest;
import com.project.tracking_system.entity.ReturnCommandLog;
import com.project.tracking_system.entity.ReturnRequestMode;
import com.project.tracking_system.entity.TrackParcel;
import com.project.tracking_system.entity.User;
import com.project.tracking_system.exception.ValidationException;
import com.project.tracking_system.repository.ReturnCommandLogRepository;
import com.project.tracking_system.service.order.payload.ReturnRequestCommandPayload;
import com.project.tracking_system.service.order.payload.ReturnRequestCommandPayloadFactory;
import com.project.tracking_system.service.order.ReturnRequestMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;
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
    private ReturnRequestCommandPayloadFactory payloadFactory;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        payloadFactory = new ReturnRequestCommandPayloadFactory();
        objectMapper = buildObjectMapper();
        commandService = new ReturnRequestCommandService(
                orderReturnRequestService,
                returnRequestMapper,
                returnCommandLogRepository,
                objectMapper,
                payloadFactory
        );
    }

    @Test
    void executeCommand_whenRepeated_returnsStoredSnapshot() {
        User user = buildUser();
        UUID requestKey = UUID.fromString("00000000-0000-0000-0000-000000000021");
        OrderReturnRequest request = buildRequest(21L, 9L, requestKey);
        OrderReturnRequest updated = buildRequest(21L, 9L, requestKey);
        RequestDto dto = buildRequestDto(requestKey.toString(), "EXCHANGE");
        CommandDto command = new CommandDto("dup-1", ReturnRequestCommandType.SET_MODE_EXCHANGE.getCode(), null);

        when(orderReturnRequestService.getOwnedRequest(requestKey, user)).thenReturn(request);
        when(orderReturnRequestService.setModeExchange(21L,
                9L,
                user,
                OrderReturnRequestService.ModeSwitchTrigger.MANUAL_DECISION)).thenReturn(updated);
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

        RequestDto first = commandService.executeCommand(requestKey,
                ReturnRequestCommandType.SET_MODE_EXCHANGE,
                command,
                user,
                ZoneOffset.UTC);
        RequestDto second = commandService.executeCommand(requestKey,
                ReturnRequestCommandType.SET_MODE_EXCHANGE,
                command,
                user,
                ZoneOffset.UTC);

        assertThat(first).isEqualTo(dto);
        assertThat(second).isEqualTo(dto);
        verify(orderReturnRequestService).setModeExchange(21L,
                9L,
                user,
                OrderReturnRequestService.ModeSwitchTrigger.MANUAL_DECISION);
        verify(returnRequestMapper).toDto(eq(updated), any());
        verify(returnCommandLogRepository, atLeast(2)).saveAndFlush(any(ReturnCommandLog.class));
    }

    @Test
    void executeCommand_whenPayloadDiffers_throwsConflict() {
        User user = buildUser();
        UUID requestKey = UUID.fromString("00000000-0000-0000-0000-000000000021");
        OrderReturnRequest request = buildRequest(21L, 9L, requestKey);
        when(orderReturnRequestService.getOwnedRequest(requestKey, user)).thenReturn(request);

        ReturnCommandLog existing = new ReturnCommandLog();
        existing.setRequestId(21L);
        existing.setIdempotencyKey("dup-2");
        existing.setPayloadHash("conflict");
        existing.setResponseSnapshot("{}");

        when(returnCommandLogRepository.findFirstByRequestIdAndIdempotencyKey(21L, "dup-2"))
                .thenReturn(Optional.of(existing));

        CommandDto command = new CommandDto("dup-2", ReturnRequestCommandType.SET_MODE_EXCHANGE.getCode(), null);

        assertThatThrownBy(() -> commandService.executeCommand(requestKey,
                ReturnRequestCommandType.SET_MODE_EXCHANGE,
                command,
                user,
                ZoneOffset.UTC))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("уже выполнена");

        verify(orderReturnRequestService, never()).setModeExchange(any(), any(), any(), any());
        verify(orderReturnRequestService, never()).setModeReturn(any(), any(), any(), any());
        verify(returnRequestMapper, never()).toDto(any(), any());
        verify(returnCommandLogRepository, never()).saveAndFlush(any(ReturnCommandLog.class));
    }

    @Test
    void executeCommand_whenConcurrentReservation_returnsStoredSnapshot() {
        User user = buildUser();
        UUID requestKey = UUID.fromString("00000000-0000-0000-0000-000000000022");
        OrderReturnRequest request = buildRequest(22L, 10L, requestKey);
        CommandDto command = new CommandDto("dup-3", ReturnRequestCommandType.SET_MODE_EXCHANGE.getCode(), null);

        RequestDto storedDto = buildRequestDto(requestKey.toString(), "EXCHANGE");
        String snapshot;
        try {
            snapshot = buildObjectMapper().writeValueAsString(storedDto);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }

        when(orderReturnRequestService.getOwnedRequest(requestKey, user)).thenReturn(request);
        ReturnCommandLog completed = new ReturnCommandLog();
        completed.setRequestId(22L);
        completed.setIdempotencyKey("dup-3");
        completed.setPayloadHash(computePayloadHash(ReturnRequestCommandType.SET_MODE_EXCHANGE, command));
        completed.setResponseSnapshot(snapshot);

        when(returnCommandLogRepository.findFirstByRequestIdAndIdempotencyKey(22L, "dup-3"))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(completed));
        when(returnCommandLogRepository.saveAndFlush(any(ReturnCommandLog.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate"));

        RequestDto result = commandService.executeCommand(requestKey,
                ReturnRequestCommandType.SET_MODE_EXCHANGE,
                command,
                user,
                ZoneOffset.UTC);

        assertThat(result.id()).isEqualTo("00000000-0000-0000-0000-000000000022");
        verify(orderReturnRequestService, never()).setModeExchange(any(), any(), any(), any());
        verify(orderReturnRequestService, never()).setModeReturn(any(), any(), any(), any());
        verify(returnRequestMapper, never()).toDto(any(), any());
    }

    @Test
    void executeCommand_whenUpdateReverseTrackWithoutPayload_throwsBadRequest() {
        User user = buildUser();
        CommandDto command = new CommandDto("dup-4", ReturnRequestCommandType.UPDATE_REVERSE_TRACK.getCode(), null);

        assertThatThrownBy(() -> commandService.executeCommand(UUID.fromString("00000000-0000-0000-0000-000000000023"),
                ReturnRequestCommandType.UPDATE_REVERSE_TRACK,
                command,
                user,
                ZoneOffset.UTC))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("требует объект payload");

        verify(orderReturnRequestService, never()).updateReverseTrack(any(), any(), any(), any(), any());
    }

    @Test
    void executeCommand_whenExchangeCommandWithoutPayload_throwsBadRequest() {
        User user = buildUser();
        CommandDto command = new CommandDto("dup-5", ReturnRequestCommandType.MARK_EXCHANGE_SENT.getCode(), null);

        assertThatThrownBy(() -> commandService.executeCommand(UUID.fromString("00000000-0000-0000-0000-000000000024"),
                ReturnRequestCommandType.MARK_EXCHANGE_SENT,
                command,
                user,
                ZoneOffset.UTC))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("требует объект payload");

        verify(orderReturnRequestService, never()).markExchangeSent(any(), any(), any(), any(), any());
    }

    @Test
    void executeCommand_whenMarkExchangeDeliveredWithoutPayload_executesWithNullMoment() {
        User user = buildUser();
        UUID requestKey = UUID.fromString("00000000-0000-0000-0000-000000000025");
        OrderReturnRequest request = buildRequest(25L, 13L, requestKey);
        OrderReturnRequest updated = buildRequest(25L, 13L, requestKey);
        RequestDto dto = buildRequestDto(requestKey.toString(), "EXCHANGE");
        CommandDto command = new CommandDto("dup-6", ReturnRequestCommandType.MARK_EXCHANGE_DELIVERED.getCode(), null);

        when(orderReturnRequestService.getOwnedRequest(requestKey, user)).thenReturn(request);
        when(orderReturnRequestService.markExchangeDelivered(eq(25L), eq(13L), eq(user), any())).thenReturn(updated);
        when(returnRequestMapper.toDto(eq(updated), any())).thenReturn(dto);

        AtomicReference<ReturnCommandLog> savedLog = new AtomicReference<>();
        when(returnCommandLogRepository.findFirstByRequestIdAndIdempotencyKey(25L, "dup-6"))
                .thenAnswer(invocation -> Optional.ofNullable(savedLog.get()));
        when(returnCommandLogRepository.saveAndFlush(any(ReturnCommandLog.class)))
                .thenAnswer(invocation -> {
                    ReturnCommandLog logEntry = invocation.getArgument(0);
                    savedLog.set(logEntry);
                    return logEntry;
                });

        RequestDto result = commandService.executeCommand(requestKey,
                ReturnRequestCommandType.MARK_EXCHANGE_DELIVERED,
                command,
                user,
                ZoneOffset.UTC);

        assertThat(result).isEqualTo(dto);
        ArgumentCaptor<ZonedDateTime> momentCaptor = ArgumentCaptor.forClass(ZonedDateTime.class);
        verify(orderReturnRequestService).markExchangeDelivered(eq(25L), eq(13L), eq(user), momentCaptor.capture());
        assertThat(momentCaptor.getValue()).isNull();
    }

    @Test
    void executeCommand_whenMarkOutboundSent_usesNormalizedStageMoment() {
        User user = buildUser();
        UUID requestKey = UUID.fromString("00000000-0000-0000-0000-000000000031");
        OrderReturnRequest request = buildRequest(31L, 15L, requestKey);
        OrderReturnRequest updated = buildRequest(31L, 15L, requestKey);
        RequestDto dto = buildRequestDto(requestKey.toString(), "RETURN");

        JsonNodeFactory factory = JsonNodeFactory.instance;
        CommandDto command = new CommandDto(
                "stage-1",
                ReturnRequestCommandType.MARK_OUTBOUND_SENT.getCode(),
                factory.objectNode().put("stageMoment", "2023-10-01T10:15:30+03:00")
        );

        when(orderReturnRequestService.getOwnedRequest(requestKey, user)).thenReturn(request);
        when(orderReturnRequestService.markOutboundSent(eq(31L), eq(15L), eq(user), any()))
                .thenReturn(updated);
        when(returnRequestMapper.toDto(eq(updated), any())).thenReturn(dto);

        AtomicReference<ReturnCommandLog> savedLog = new AtomicReference<>();
        when(returnCommandLogRepository.findFirstByRequestIdAndIdempotencyKey(31L, "stage-1"))
                .thenAnswer(invocation -> Optional.ofNullable(savedLog.get()));
        when(returnCommandLogRepository.saveAndFlush(any(ReturnCommandLog.class)))
                .thenAnswer(invocation -> {
                    ReturnCommandLog logEntry = invocation.getArgument(0);
                    savedLog.set(logEntry);
                    return logEntry;
                });

        RequestDto result = commandService.executeCommand(requestKey,
                ReturnRequestCommandType.MARK_OUTBOUND_SENT,
                command,
                user,
                ZoneOffset.UTC);

        assertThat(result).isEqualTo(dto);
        ArgumentCaptor<ZonedDateTime> momentCaptor = ArgumentCaptor.forClass(ZonedDateTime.class);
        verify(orderReturnRequestService).markOutboundSent(eq(31L), eq(15L), eq(user), momentCaptor.capture());
        assertThat(momentCaptor.getValue()).isEqualTo(ZonedDateTime.parse("2023-10-01T07:15:30Z"));
    }

    @Test
    void executeCommand_whenRegisterExchangeParcel_normalizesTrackAndStageMoment() {
        User user = buildUser();
        UUID requestKey = UUID.fromString("00000000-0000-0000-0000-000000000032");
        OrderReturnRequest request = buildRequest(32L, 16L, requestKey);
        OrderReturnRequest updated = buildRequest(32L, 16L, requestKey);
        RequestDto dto = buildRequestDto(requestKey.toString(), "EXCHANGE");

        JsonNodeFactory factory = JsonNodeFactory.instance;
        CommandDto command = new CommandDto(
                "stage-2",
                ReturnRequestCommandType.REGISTER_EXCHANGE_PARCEL.getCode(),
                factory.objectNode()
                        .put("exchangeTrack", "  ex123  ")
                        .put("stageMoment", "2024-01-15T12:00:00+02:00")
        );

        when(orderReturnRequestService.getOwnedRequest(requestKey, user)).thenReturn(request);
        when(orderReturnRequestService.registerExchangeParcel(eq(32L), eq(16L), eq(user), any(), any()))
                .thenReturn(updated);
        when(returnRequestMapper.toDto(eq(updated), any())).thenReturn(dto);

        AtomicReference<ReturnCommandLog> savedLog = new AtomicReference<>();
        when(returnCommandLogRepository.findFirstByRequestIdAndIdempotencyKey(32L, "stage-2"))
                .thenAnswer(invocation -> Optional.ofNullable(savedLog.get()));
        when(returnCommandLogRepository.saveAndFlush(any(ReturnCommandLog.class)))
                .thenAnswer(invocation -> {
                    ReturnCommandLog logEntry = invocation.getArgument(0);
                    savedLog.set(logEntry);
                    return logEntry;
                });

        RequestDto result = commandService.executeCommand(requestKey,
                ReturnRequestCommandType.REGISTER_EXCHANGE_PARCEL,
                command,
                user,
                ZoneOffset.UTC);

        assertThat(result).isEqualTo(dto);
        ArgumentCaptor<String> trackCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<ZonedDateTime> momentCaptor = ArgumentCaptor.forClass(ZonedDateTime.class);
        verify(orderReturnRequestService).registerExchangeParcel(eq(32L), eq(16L), eq(user), trackCaptor.capture(), momentCaptor.capture());
        assertThat(trackCaptor.getValue()).isEqualTo("EX123");
        assertThat(momentCaptor.getValue()).isEqualTo(ZonedDateTime.parse("2024-01-15T10:00:00Z"));
    }

    private String computePayloadHash(ReturnRequestCommandType type, CommandDto command) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(type.name().getBytes(StandardCharsets.UTF_8));
            digest.update(nullSafeBytes(command.action()));
            ReturnRequestCommandPayload payload = payloadFactory.create(type, command.payload());
            payload.updateDigest(digest, objectMapper);
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private byte[] nullSafeBytes(String value) {
        return value != null ? value.getBytes(StandardCharsets.UTF_8) : new byte[0];
    }

    /**
     * Создаёт ObjectMapper с поддержкой JavaTime API для сериализации фикстур.
     */
    private ObjectMapper buildObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return mapper;
    }

    private User buildUser() {
        User user = new User();
        user.setId(5L);
        return user;
    }

    private OrderReturnRequest buildRequest(Long requestId, Long parcelId, UUID requestKey) {
        OrderReturnRequest request = new OrderReturnRequest();
        request.setId(requestId);
        request.setIdempotencyKey(requestKey != null ? requestKey.toString() : null);
        TrackParcel parcel = new TrackParcel();
        parcel.setId(parcelId);
        request.setParcel(parcel);
        return request;
    }

    /**
     * Собирает DTO заявки с актуальным списком полей публичного API.
     */
    private RequestDto buildRequestDto(String uuid, String mode) {
        ZonedDateTime timestamp = ZonedDateTime.parse("2024-01-01T10:15:30Z");
        return new RequestDto(
                uuid,
                mode,
                "NEW",
                10L,
                20L,
                30L,
                40L,
                "Причина",
                "Комментарий",
                "BY123",
                "EX123",
                false,
                timestamp,
                timestamp
        );
    }
}
