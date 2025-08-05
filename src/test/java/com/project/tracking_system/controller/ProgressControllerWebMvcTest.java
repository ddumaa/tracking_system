package com.project.tracking_system.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.tracking_system.dto.TrackProcessingProgressDTO;
import com.project.tracking_system.dto.TrackStatusUpdateDTO;
import com.project.tracking_system.entity.Role;
import com.project.tracking_system.entity.User;
import com.project.tracking_system.service.track.ProgressAggregatorService;
import com.project.tracking_system.service.track.TrackingResultCacheService;
import com.project.tracking_system.service.track.InvalidTrackCacheService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;

import java.util.List;

import static org.mockito.Mockito.*;

/**
 * Интеграционные тесты REST-эндпоинтов {@link ProgressController}.
 * <p>
 * Используется {@link WebMvcTest} без фильтров безопасности для изолированного
 * тестирования контроллера.
 * </p>
 */
@ExtendWith(SpringExtension.class)
@WebMvcTest(ProgressController.class)
@AutoConfigureMockMvc(addFilters = false)
class ProgressControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ProgressAggregatorService progressAggregatorService;

    @MockBean
    private TrackingResultCacheService trackingResultCacheService;

    @MockBean
    private InvalidTrackCacheService invalidTrackCacheService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Проверяем, что анонимный пользователь получает пустой прогресс.
     */
    @Test
    void getLatestProgress_Anonymous_ReturnsEmptyDto() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/app/progress/latest"))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.batchId").value(0))
                .andExpect(MockMvcResultMatchers.jsonPath("$.processed").value(0))
                .andExpect(MockMvcResultMatchers.jsonPath("$.total").value(0))
                .andExpect(MockMvcResultMatchers.jsonPath("$.elapsed").value("0:00"));

        verify(progressAggregatorService, never()).getLatestBatchId(any());
    }

    /**
     * Проверяем, что аутентифицированный пользователь получает актуальные данные.
     */
    @Test
    void getLatestProgress_User_ReturnsDtoFromService() throws Exception {
        User user = buildUser(1L);
        when(progressAggregatorService.getLatestBatchId(1L)).thenReturn(7L);
        TrackProcessingProgressDTO dto = new TrackProcessingProgressDTO(7L, 2, 5, "0:10");
        when(progressAggregatorService.getProgress(7L)).thenReturn(dto);

        mockMvc.perform(MockMvcRequestBuilders.get("/app/progress/latest")
                        .with(SecurityMockMvcRequestPostProcessors.user(user)))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.content().json(objectMapper.writeValueAsString(dto)));

        verify(progressAggregatorService).getLatestBatchId(1L);
        verify(progressAggregatorService).getProgress(7L);
    }

    /**
     * Проверяем получение списка последних результатов пользователя.
     */
    @Test
    void getLatestResults_ReturnsListFromService() throws Exception {
        User user = buildUser(1L);
        List<TrackStatusUpdateDTO> list = List.of(new TrackStatusUpdateDTO(1L, "T123", "OK", 1, 1));
        when(trackingResultCacheService.getLatestResults(1L)).thenReturn(list);

        mockMvc.perform(MockMvcRequestBuilders.get("/app/results/latest")
                        .with(SecurityMockMvcRequestPostProcessors.user(user)))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.content().json(objectMapper.writeValueAsString(list)));

        verify(trackingResultCacheService).getLatestResults(1L);
    }

    /**
     * Проверяем очистку результатов пользователя.
     */
    @Test
    void clearResults_InvokesService() throws Exception {
        User user = buildUser(2L);

        mockMvc.perform(MockMvcRequestBuilders.post("/app/results/clear")
                        .with(SecurityMockMvcRequestPostProcessors.user(user)))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.content().string("cleared"));

        verify(trackingResultCacheService).clearResults(2L);
    }

    /**
     * Утилитарный метод создания пользователя для тестов.
     */
    private User buildUser(Long id) {
        User user = new User();
        user.setId(id);
        user.setEmail("user" + id + "@example.com");
        user.setPassword("pass");
        user.setTimeZone("UTC");
        user.setRole(Role.ROLE_USER);
        return user;
    }
}
