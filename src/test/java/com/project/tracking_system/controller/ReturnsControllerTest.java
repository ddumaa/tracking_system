package com.project.tracking_system.controller;

import com.project.tracking_system.dto.ReturnRequestAvailableActionsDto;
import com.project.tracking_system.dto.ReturnRequestDto;
import com.project.tracking_system.entity.OrderReturnRequest;
import com.project.tracking_system.entity.TrackParcel;
import com.project.tracking_system.entity.User;
import com.project.tracking_system.service.order.OrderReturnRequestService;
import com.project.tracking_system.service.order.ReturnRequestCommandService;
import com.project.tracking_system.service.order.ReturnRequestMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
import java.util.Set;

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

    @Test
    void registerReturn_returnsMappedDto() throws Exception {
        User principal = buildUser();
        UsernamePasswordAuthenticationToken auth = authentication(principal);

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

        ReturnRequestDto responseDto = new ReturnRequestDto(
                15L,
                "Регистрация",
                "Размер не подошёл",
                "Комментарий",
                "BY123",
                false,
                null,
                null,
                null,
                null,
                null
        );
        when(returnRequestMapper.toDto(eq(request), any())).thenReturn(responseDto);

        mockMvc.perform(post("/api/v1/returns")
                        .with(auth)
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
                .andExpect(jsonPath("$.id", equalTo(15)))
                .andExpect(jsonPath("$.reason", equalTo("Размер не подошёл")));

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
        UsernamePasswordAuthenticationToken auth = authentication(principal);

        ReturnRequestDto responseDto = new ReturnRequestDto(21L, "EXCHANGE", null, null, null, false, null, null, null, null, null);
        when(returnRequestCommandService.executeCommand(eq(21L), any(), any(), eq(principal), any()))
                .thenReturn(responseDto);

        mockMvc.perform(post("/api/v1/returns/21/commands")
                        .with(auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{" +
                                "\"idempotencyKey\":\"cmd-1\"," +
                                "\"command\":\"start_exchange\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", equalTo(21)))
                .andExpect(jsonPath("$.status", equalTo("EXCHANGE")));
        verify(returnRequestCommandService).executeCommand(eq(21L), any(), any(), eq(principal), any());
    }

    @Test
    void executeCommand_updateDetails_callsServiceWithPayload() throws Exception {
        User principal = buildUser();
        UsernamePasswordAuthenticationToken auth = authentication(principal);

        ReturnRequestDto responseDto = new ReturnRequestDto(30L, "REGISTERED", null, "Комментарий", "BY000", false, null, null, null, null, null);
        when(returnRequestCommandService.executeCommand(eq(30L), any(), any(), eq(principal), any()))
                .thenReturn(responseDto);

        mockMvc.perform(post("/api/v1/returns/30/commands")
                        .with(auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{" +
                                "\"idempotencyKey\":\"cmd-2\"," +
                                "\"command\":\"update_details\"," +
                                "\"reverseTrackNumber\":\"BY000\"," +
                                "\"comment\":\"Комментарий\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reverseTrackNumber", equalTo("BY000")));
        verify(returnRequestCommandService).executeCommand(eq(30L), any(), any(), eq(principal), any());
    }

    @Test
    void getReturnRequest_returnsMappedDto() throws Exception {
        User principal = buildUser();
        UsernamePasswordAuthenticationToken auth = authentication(principal);

        OrderReturnRequest request = new OrderReturnRequest();
        when(orderReturnRequestService.getOwnedRequest(51L, principal)).thenReturn(request);
        when(returnRequestMapper.toDto(eq(request), any()))
                .thenReturn(new ReturnRequestDto(51L, "REGISTERED", null, null, null, false, null, null, null, null, null));

        mockMvc.perform(get("/api/v1/returns/51")
                        .with(auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", equalTo(51)));
    }

    @Test
    void getAvailableActions_returnsDto() throws Exception {
        User principal = buildUser();
        UsernamePasswordAuthenticationToken auth = authentication(principal);

        OrderReturnRequest request = new OrderReturnRequest();
        when(orderReturnRequestService.getOwnedRequest(52L, principal)).thenReturn(request);
        when(returnRequestMapper.toAvailableActions(request))
                .thenReturn(new ReturnRequestAvailableActionsDto(true, false, true, false, false, false, null, Set.of()));

        mockMvc.perform(get("/api/v1/returns/52/available-actions")
                        .with(auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.startExchange", equalTo(true)))
                .andExpect(jsonPath("$.closeWithoutExchange", equalTo(true)));
    }

    private UsernamePasswordAuthenticationToken authentication(User user) {
        return new UsernamePasswordAuthenticationToken(user, user.getPassword(), user.getAuthorities());
    }

    private User buildUser() {
        User user = new User();
        user.setId(1L);
        user.setEmail("user@example.com");
        user.setPassword("password");
        user.setRole(com.project.tracking_system.entity.Role.ROLE_USER);
        user.setTimeZone("UTC");
        return user;
    }
}
