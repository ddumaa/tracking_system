package com.project.tracking_system.controller;

import com.project.tracking_system.dto.TrackChainItemDto;
import com.project.tracking_system.dto.TrackDetailsDto;
import com.project.tracking_system.entity.Role;
import com.project.tracking_system.entity.User;
import com.project.tracking_system.entity.TrackParcel;
import com.project.tracking_system.service.order.ExchangeApprovalResult;
import com.project.tracking_system.service.order.OrderReturnRequestService;
import com.project.tracking_system.service.track.TrackParcelService;
import com.project.tracking_system.service.track.TrackViewService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
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
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Тесты REST-эндпоинтов возвратов/обменов {@link TrackController}.
 */
@ExtendWith(SpringExtension.class)
@WebMvcTest(TrackController.class)
@AutoConfigureMockMvc(addFilters = false)
class TrackControllerReturnTest {

    private static final String DEFAULT_REASON = "Размер не подошёл";
    private static final String DEFAULT_COMMENT = "Хочу выбрать другую модель";
    private static final String DEFAULT_REVERSE_TRACK = "BY1234567890";
    private static final OffsetDateTime DEFAULT_REQUESTED_AT = OffsetDateTime.of(
            2023, 5, 10, 12, 0, 0, 0, ZoneOffset.UTC
    );

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TrackViewService trackViewService;
    @MockBean
    private TrackParcelService trackParcelService;
    @MockBean
    private OrderReturnRequestService orderReturnRequestService;

    @Test
    void registerReturn_ReturnsUpdatedDetails() throws Exception {
        User principal = buildUser();
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                principal,
                principal.getPassword(),
                principal.getAuthorities()
        );

        TrackDetailsDto dto = new TrackDetailsDto(
                5L,
                "AB123",
                "Belpost",
                "Вручена",
                null,
                null,
                List.of(),
                true,
                null,
                false,
                "UTC",
                10L,
                false,
                List.of(),
                null,
                false,
                false
        );

        when(trackViewService.getTrackDetails(5L, 1L)).thenReturn(dto);

        mockMvc.perform(post("/api/v1/tracks/5/returns")
                        .with(authentication(auth))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{" +
                                "\"idempotencyKey\":\"key\"," +
                                "\"reason\":\"" + DEFAULT_REASON + "\"," +
                                "\"requestedAt\":\"" + DEFAULT_REQUESTED_AT + "\"," +
                                "\"comment\":\"" + DEFAULT_COMMENT + "\"," +
                                "\"reverseTrackNumber\":\"" + DEFAULT_REVERSE_TRACK + "\"," +
                                "\"isExchange\":false" +
                                "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(5L))
                .andExpect(jsonPath("$.canRegisterReturn").value(false));

        Mockito.verify(orderReturnRequestService).registerReturn(
                eq(5L),
                eq(principal),
                eq("key"),
                eq(DEFAULT_REASON),
                eq(DEFAULT_COMMENT),
                argThat(value -> value.equals(DEFAULT_REQUESTED_AT.atZoneSameInstant(ZoneOffset.UTC))),
                eq(DEFAULT_REVERSE_TRACK),
                eq(false)
        );
    }

    @Test
    void registerReturn_ForExchangeRequest_ForwardsFlag() throws Exception {
        User principal = buildUser();
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                principal,
                principal.getPassword(),
                principal.getAuthorities()
        );

        TrackDetailsDto dto = new TrackDetailsDto(
                8L,
                "AB123",
                "Belpost",
                "Вручена",
                null,
                null,
                List.of(),
                true,
                null,
                false,
                "UTC",
                12L,
                false,
                List.of(),
                null,
                false,
                false
        );

        when(trackViewService.getTrackDetails(8L, 1L)).thenReturn(dto);

        mockMvc.perform(post("/api/v1/tracks/8/returns")
                        .with(authentication(auth))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{" +
                                "\"idempotencyKey\":\"exchange\"," +
                                "\"reason\":\"" + DEFAULT_REASON + "\"," +
                                "\"requestedAt\":\"" + DEFAULT_REQUESTED_AT + "\"," +
                                "\"isExchange\":true" +
                                "}"))
                .andExpect(status().isOk());

        Mockito.verify(orderReturnRequestService).registerReturn(
                eq(8L),
                eq(principal),
                eq("exchange"),
                eq(DEFAULT_REASON),
                eq(null),
                argThat(value -> value.equals(DEFAULT_REQUESTED_AT.atZoneSameInstant(ZoneOffset.UTC))),
                eq(null),
                eq(true)
        );
    }

    @Test
    void approveExchange_WhenConflict_Returns409() throws Exception {
        User principal = buildUser();
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                principal,
                principal.getPassword(),
                principal.getAuthorities()
        );

        when(orderReturnRequestService.approveExchange(eq(7L), eq(9L), eq(principal)))
                .thenThrow(new IllegalStateException("В эпизоде уже запущен обмен"));

        mockMvc.perform(post("/api/v1/tracks/9/returns/7/exchange")
                        .with(authentication(auth))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isConflict());

        Mockito.verify(orderReturnRequestService).approveExchange(eq(7L), eq(9L), eq(principal));
        Mockito.verify(trackViewService, Mockito.never()).getTrackDetails(any(), any());
        Mockito.verify(trackViewService, Mockito.never()).toChainItem(any(), any());
    }

    @Test
    void approveExchange_ReturnsDetailsAndExchangeItem() throws Exception {
        User principal = buildUser();
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                principal,
                principal.getPassword(),
                principal.getAuthorities()
        );

        TrackDetailsDto dto = new TrackDetailsDto(
                9L,
                "AB123",
                "Belpost",
                "Вручена",
                null,
                null,
                List.of(),
                true,
                null,
                false,
                "UTC",
                10L,
                false,
                List.of(),
                null,
                false,
                false
        );

        TrackParcel replacement = new TrackParcel();
        replacement.setId(33L);
        replacement.setNumber("EX123");
        replacement.setExchange(true);

        ExchangeApprovalResult result = new ExchangeApprovalResult(null, replacement);

        when(orderReturnRequestService.approveExchange(eq(7L), eq(9L), eq(principal)))
                .thenReturn(result);
        when(trackViewService.getTrackDetails(9L, 1L)).thenReturn(dto);
        TrackChainItemDto chainItemDto = new TrackChainItemDto(33L, "EX123", true, false);
        when(trackViewService.toChainItem(replacement, 9L)).thenReturn(chainItemDto);

        mockMvc.perform(post("/api/v1/tracks/9/returns/7/exchange")
                        .with(authentication(auth))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.details.id").value(9L))
                .andExpect(jsonPath("$.exchange.id").value(33L))
                .andExpect(jsonPath("$.exchange.exchange").value(true));

        Mockito.verify(orderReturnRequestService).approveExchange(eq(7L), eq(9L), eq(principal));
        Mockito.verify(trackViewService).getTrackDetails(9L, 1L);
        Mockito.verify(trackViewService).toChainItem(replacement, 9L);
    }

    private User buildUser() {
        User user = new User();
        user.setId(1L);
        user.setEmail("user@example.com");
        user.setPassword("secret");
        user.setRole(Role.ROLE_USER);
        user.setTimeZone("UTC");
        return user;
    }
}

