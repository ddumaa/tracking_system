package com.project.tracking_system.controller;

import com.project.tracking_system.entity.Role;
import com.project.tracking_system.entity.User;
import com.project.tracking_system.service.order.OrderReturnRequestService;
import com.project.tracking_system.service.order.ReturnRequestActionMapper;
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

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Тесты эндпоинтов истории и жизненного цикла {@link TrackController}.
 */
@ExtendWith(SpringExtension.class)
@WebMvcTest(TrackController.class)
@AutoConfigureMockMvc(addFilters = false)
class TrackControllerHistoryLifecycleTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TrackViewService trackViewService;
    @MockBean
    private TrackParcelService trackParcelService;
    @MockBean
    private OrderReturnRequestService orderReturnRequestService;
    @MockBean
    private ReturnRequestActionMapper returnRequestActionMapper;

    /**
     * Проверяет, что при отсутствии событий возвращается пустой массив.
     */
    @Test
    void getTrackHistory_WhenNoEvents_ReturnsEmptyArray() throws Exception {
        User principal = buildUser();
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                principal,
                principal.getPassword(),
                principal.getAuthorities()
        );

        when(trackViewService.getTrackHistory(5L, 1L)).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/tracks/5/history")
                        .with(authentication(auth))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(0));

        Mockito.verify(trackViewService).getTrackHistory(eq(5L), eq(1L));
    }

    /**
     * Проверяет, что при отсутствии этапов жизненного цикла возвращается пустой массив.
     */
    @Test
    void getTrackLifecycle_WhenNoStages_ReturnsEmptyArray() throws Exception {
        User principal = buildUser();
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                principal,
                principal.getPassword(),
                principal.getAuthorities()
        );

        when(trackViewService.getTrackLifecycle(7L, 1L)).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/tracks/7/lifecycle")
                        .with(authentication(auth))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(0));

        Mockito.verify(trackViewService).getTrackLifecycle(eq(7L), eq(1L));
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

