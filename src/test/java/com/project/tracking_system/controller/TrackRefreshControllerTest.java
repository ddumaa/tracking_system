package com.project.tracking_system.controller;

import com.project.tracking_system.dto.TrackDetailsDto;
import com.project.tracking_system.entity.Role;
import com.project.tracking_system.entity.User;
import com.project.tracking_system.service.track.TrackRefreshService;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Интеграционный тест для {@link TrackRefreshController} в сценарии отказа по кулдауну.
 */
@ExtendWith(SpringExtension.class)
@WebMvcTest(TrackRefreshController.class)
@AutoConfigureMockMvc(addFilters = false)
class TrackRefreshControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TrackRefreshService trackRefreshService;

    /**
     * Проверяет, что контроллер возвращает код 200 и DTO с запретом на обновление,
     * если сервис сигнализирует о нарушении кулдауна.
     */
    @Test
    void refresh_WhenCooldownViolation_ReturnsCooldownDto() throws Exception {
        String nextRefreshAt = "2025-03-10T15:30:00+03:00";
        TrackDetailsDto cooldownDetails = new TrackDetailsDto(
                7L,
                "RB123456789CN",
                "Belpost",
                null,
                List.of(),
                false,
                nextRefreshAt,
                true,
                "Europe/Minsk",
                15L,
                false,
                List.of()
        );
        when(trackRefreshService.refreshTrack(eq(7L), eq(3L))).thenReturn(cooldownDetails);

        User principal = new User();
        principal.setId(3L);
        principal.setEmail("user@example.com");
        principal.setPassword("secret");
        principal.setRole(Role.ROLE_USER);
        principal.setTimeZone("Europe/Minsk");

        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                principal,
                principal.getPassword(),
                principal.getAuthorities()
        );

        mockMvc.perform(post("/api/v1/tracks/7/refresh")
                        .with(authentication(auth))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.refreshAllowed").value(false))
                .andExpect(jsonPath("$.nextRefreshAt").value(nextRefreshAt));

        Mockito.verify(trackRefreshService).refreshTrack(7L, 3L);
    }
}
