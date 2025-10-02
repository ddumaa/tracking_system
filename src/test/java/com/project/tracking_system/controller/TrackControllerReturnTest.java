package com.project.tracking_system.controller;

import com.project.tracking_system.dto.TrackDetailsDto;
import com.project.tracking_system.entity.Role;
import com.project.tracking_system.entity.User;
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

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
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
                        .content("{\"idempotencyKey\":\"key\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(5L))
                .andExpect(jsonPath("$.canRegisterReturn").value(false));

        Mockito.verify(orderReturnRequestService).registerReturn(eq(5L), eq(principal), eq("key"));
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

