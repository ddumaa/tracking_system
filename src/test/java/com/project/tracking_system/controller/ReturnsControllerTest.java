package com.project.tracking_system.controller;

import com.project.tracking_system.dto.AvailableActionsDto;
import com.project.tracking_system.dto.CommandDto;
import com.project.tracking_system.dto.RequestDto;
import com.project.tracking_system.entity.OrderReturnRequest;
import com.project.tracking_system.entity.ReturnRequestAction;
import com.project.tracking_system.controller.ReturnRequestCommandType;
import com.project.tracking_system.entity.User;
import com.project.tracking_system.service.admin.AppInfoService;
import com.project.tracking_system.service.order.OrderReturnRequestService;
import com.project.tracking_system.service.ratelimit.Bucket4jRateLimiter;
import com.project.tracking_system.service.order.ReturnRequestCommandService;
import com.project.tracking_system.service.order.ReturnRequestMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Тесты REST-эндпоинтов {@link ReturnsController}.
 */
@ExtendWith(SpringExtension.class)
@WebMvcTest(ReturnsController.class)
@AutoConfigureMockMvc(addFilters = false)
class ReturnsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private OrderReturnRequestService orderReturnRequestService;

    @MockBean
    private ReturnRequestMapper returnRequestMapper;

    @MockBean
    private ReturnRequestCommandService returnRequestCommandService;

    @MockBean
    private AppInfoService appInfoService;

    @MockBean
    private Bucket4jRateLimiter bucket4jRateLimiter;

    @Test
    void registerReturn_returnsMappedDto() throws Exception {
        User principal = buildUser();
        UsernamePasswordAuthenticationToken auth = buildAuthentication(principal);

        OrderReturnRequest request = new OrderReturnRequest();
        when(orderReturnRequestService.registerReturn(
                eq(7L),
                eq(principal),
                eq("key"),
                eq("Размер не подошёл"),
                eq("Комментарий"),
                any(),
                eq("BY123"),
                eq(false)
        )).thenReturn(request);

        RequestDto responseDto = buildRequestDto("00000000-0000-0000-0000-000000000015", "RETURN", "NEW", 17L, 7L);
        when(returnRequestMapper.toDto(eq(request), any())).thenReturn(responseDto);

        mockMvc.perform(post("/api/v1/returns")
                        .with(authentication(auth))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{" +
                                "\"parcelId\":7," +
                                "\"idempotencyKey\":\"key\"," +
                                "\"reason\":\"Размер не подошёл\"," +
                                "\"requestedAt\":\"2024-05-10T10:00:00Z\"," +
                                "\"comment\":\"Комментарий\"," +
                                "\"reverseTrackNumber\":\"BY123\"," +
                                "\"isExchange\":false" +
                                "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", equalTo("00000000-0000-0000-0000-000000000015")))
                .andExpect(jsonPath("$.stage", equalTo("NEW")))
                .andExpect(jsonPath("$.reverseTrack", equalTo("BY123")))
                .andExpect(jsonPath("$.mode", equalTo("RETURN")));

        verify(orderReturnRequestService).registerReturn(
                eq(7L),
                eq(principal),
                eq("key"),
                eq("Размер не подошёл"),
                eq("Комментарий"),
                eq(OffsetDateTime.parse("2024-05-10T10:00:00Z").atZoneSameInstant(ZoneOffset.UTC)),
                eq("BY123"),
                eq(false)
        );
    }

    @Test
    void executeCommand_startExchange_delegatesToService() throws Exception {
        User principal = buildUser();
        UsernamePasswordAuthenticationToken auth = buildAuthentication(principal);

        String requestUuid = "00000000-0000-0000-0000-000000000021";
        RequestDto responseDto = buildRequestDto(requestUuid, "EXCHANGE", "EXCHANGE_REGISTERED", 17L, 11L);
        when(returnRequestCommandService.executeCommand(eq(UUID.fromString(requestUuid)), any(), any(), eq(principal), any()))
                .thenReturn(responseDto);

        mockMvc.perform(post("/api/v1/returns/" + requestUuid + "/commands")
                        .with(authentication(auth))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idempotencyKey\":\"cmd-1\",\"action\":\"SET_MODE_EXCHANGE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", equalTo("00000000-0000-0000-0000-000000000021")))
                .andExpect(jsonPath("$.mode", equalTo("EXCHANGE")));
        ArgumentCaptor<ReturnRequestCommandType> typeCaptor = ArgumentCaptor.forClass(ReturnRequestCommandType.class);
        ArgumentCaptor<CommandDto> commandCaptor = ArgumentCaptor.forClass(CommandDto.class);

        verify(returnRequestCommandService).executeCommand(eq(UUID.fromString(requestUuid)), typeCaptor.capture(), commandCaptor.capture(), eq(principal), any());
        assertThat(typeCaptor.getValue()).isEqualTo(ReturnRequestCommandType.SET_MODE_EXCHANGE);
        assertThat(commandCaptor.getValue().action()).isEqualTo(ReturnRequestCommandType.SET_MODE_EXCHANGE.getCode());
        assertThat(commandCaptor.getValue().idempotencyKey()).isEqualTo("cmd-1");
        assertThat(commandCaptor.getValue().payload()).isNull();
    }

    @Test
    void executeCommand_updateDetails_callsServiceWithPayload() throws Exception {
        User principal = buildUser();
        UsernamePasswordAuthenticationToken auth = buildAuthentication(principal);

        String requestUuid = "00000000-0000-0000-0000-000000000030";
        RequestDto responseDto = buildRequestDto(requestUuid, "RETURN", "INBOUND_PICKED_UP", 17L, 30L);
        when(returnRequestCommandService.executeCommand(eq(UUID.fromString(requestUuid)), any(), any(), eq(principal), any()))
                .thenReturn(responseDto);

        mockMvc.perform(post("/api/v1/returns/" + requestUuid + "/commands")
                        .with(authentication(auth))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idempotencyKey\":\"cmd-2\",\"action\":\"UPDATE_REVERSE_TRACK\",\"payload\":{\"reverseTrack\":\"BY000\",\"comment\":\"Комментарий\"}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stage", equalTo("INBOUND_PICKED_UP")));
        ArgumentCaptor<ReturnRequestCommandType> typeCaptor = ArgumentCaptor.forClass(ReturnRequestCommandType.class);
        ArgumentCaptor<CommandDto> commandCaptor = ArgumentCaptor.forClass(CommandDto.class);

        verify(returnRequestCommandService).executeCommand(eq(UUID.fromString(requestUuid)), typeCaptor.capture(), commandCaptor.capture(), eq(principal), any());
        assertThat(typeCaptor.getValue()).isEqualTo(ReturnRequestCommandType.UPDATE_REVERSE_TRACK);
        assertThat(commandCaptor.getValue().action()).isEqualTo(ReturnRequestCommandType.UPDATE_REVERSE_TRACK.getCode());
        assertThat(commandCaptor.getValue().payload().get("reverseTrack").asText()).isEqualTo("BY000");
        assertThat(commandCaptor.getValue().payload().get("comment").asText()).isEqualTo("Комментарий");
    }

    @Test
    void getReturnRequest_returnsMappedDto() throws Exception {
        User principal = buildUser();
        UsernamePasswordAuthenticationToken auth = buildAuthentication(principal);

        OrderReturnRequest request = new OrderReturnRequest();
        String requestUuid = "00000000-0000-0000-0000-000000000051";
        when(orderReturnRequestService.getOwnedRequest(UUID.fromString(requestUuid), principal)).thenReturn(request);
        when(returnRequestMapper.toDto(eq(request), any()))
                .thenReturn(buildRequestDto(requestUuid, "RETURN", "NEW", 17L, 51L));

        mockMvc.perform(get("/api/v1/returns/" + requestUuid)
                        .with(authentication(auth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", equalTo("00000000-0000-0000-0000-000000000051")))
                .andExpect(jsonPath("$.actions").doesNotExist());
    }

    @Test
    void getAvailableActions_returnsDto() throws Exception {
        User principal = buildUser();
        UsernamePasswordAuthenticationToken auth = buildAuthentication(principal);

        OrderReturnRequest request = new OrderReturnRequest();
        String requestUuid = "00000000-0000-0000-0000-000000000052";
        when(orderReturnRequestService.getOwnedRequest(UUID.fromString(requestUuid), principal)).thenReturn(request);
        when(returnRequestMapper.toAvailableActions(request))
                .thenReturn(new AvailableActionsDto(List.of(
                        ReturnRequestAction.SET_MODE_EXCHANGE.getCode(),
                        ReturnRequestAction.CLOSE_REQUEST.getCode()
                )));

        mockMvc.perform(get("/api/v1/returns/" + requestUuid + "/available-actions")
                        .with(authentication(auth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.actions[0]", equalTo(ReturnRequestAction.SET_MODE_EXCHANGE.getCode())))
                .andExpect(jsonPath("$.actions[1]", equalTo(ReturnRequestAction.CLOSE_REQUEST.getCode())))
                .andExpect(jsonPath("$.actionCodes").doesNotExist());
    }

    /**
     * Создаёт объект аутентификации Spring Security для тестового пользователя.
     */
    private UsernamePasswordAuthenticationToken buildAuthentication(User user) {
        return new UsernamePasswordAuthenticationToken(user, user.getPassword(), user.getAuthorities());
    }

    /**
     * Формирует DTO заявки с актуальным набором полей публичного API.
     */
    private RequestDto buildRequestDto(String uuid,
                                       String mode,
                                       String stage,
                                       Long storeId,
                                       Long parcelId) {
        ZonedDateTime timestamp = ZonedDateTime.parse("2024-01-01T10:15:30Z");
        return new RequestDto(
                uuid,
                mode,
                stage,
                storeId,
                99L,
                parcelId,
                5L,
                "Причина",
                "Комментарий",
                "BY123",
                "EX123",
                false,
                timestamp,
                timestamp
        );
    }

    /**
     * Создаёт минимально необходимый профиль пользователя для имитации авторизации.
     */
    private User buildUser() {
        User user = new User();
        user.setId(1L);
        user.setEmail("user@example.com");
        user.setPassword("pwd");
        return user;
    }
}
