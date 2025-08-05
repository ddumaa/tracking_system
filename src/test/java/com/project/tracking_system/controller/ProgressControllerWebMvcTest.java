package com.project.tracking_system.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.tracking_system.dto.TrackProcessingProgressDTO;
import com.project.tracking_system.dto.TrackStatusUpdateDTO;
import com.project.tracking_system.entity.Role;
import com.project.tracking_system.entity.User;
import com.project.tracking_system.service.track.InvalidTrack;
import com.project.tracking_system.service.track.InvalidTrackCacheService;
import com.project.tracking_system.service.track.InvalidTrackReason;
import com.project.tracking_system.service.track.ProgressAggregatorService;
import com.project.tracking_system.service.track.TrackingResultCacheService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * WebMvc-тесты {@link ProgressController}.
 * <p>
 * Тесты проверяют REST-эндпоинты контроллера и взаимодействие с зависимыми сервисами.
 * Для изоляции контроллера используем {@link WebMvcTest} и подменяем сервисы через {@link MockBean}.
 * </p>
 */
@WebMvcTest(ProgressController.class)
@AutoConfigureMockMvc(addFilters = false)
class ProgressControllerWebMvcTest {

    /** Исполнитель HTTP-запросов к контроллеру. */
    @Autowired
    private MockMvc mockMvc;

    /** Сериализатор JSON, предоставленный контекстом Spring. */
    @Autowired
    private ObjectMapper objectMapper;

    /** Мок сервиса агрегатора прогресса. */
    @MockBean
    private ProgressAggregatorService progressAggregatorService;

    /** Мок сервиса результатов обработки. */
    @MockBean
    private TrackingResultCacheService trackingResultCacheService;

    /** Мок сервиса некорректных треков. */
    @MockBean
    private InvalidTrackCacheService invalidTrackCacheService;

    /**
     * Проверяет получение прогресса по конкретному идентификатору батча.
     */
    @Test
    void getProgress_ReturnsProgressFromService() throws Exception {
        TrackProcessingProgressDTO dto = new TrackProcessingProgressDTO(5L, 2, 3, "0:05");
        when(progressAggregatorService.getProgress(5L)).thenReturn(dto);

        mockMvc.perform(get("/app/progress/5"))
                .andExpect(status().isOk())
                .andExpect(content().json(objectMapper.writeValueAsString(dto)));

        verify(progressAggregatorService).getProgress(5L);
    }

    /**
     * Анонимный пользователь должен получать пустой прогресс.
     */
    @Test
    void getLatestProgress_Anonymous_ReturnsEmptyDto() throws Exception {
        mockMvc.perform(get("/app/progress/latest"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.batchId").value(0))
                .andExpect(jsonPath("$.processed").value(0))
                .andExpect(jsonPath("$.total").value(0))
                .andExpect(jsonPath("$.elapsed").value("0:00"));

        verifyNoInteractions(progressAggregatorService);
    }

    /**
     * Проверяет получение последнего прогресса для авторизованного пользователя.
     */
    @Test
    void getLatestProgress_User_ReturnsDtoFromService() throws Exception {
        User user = buildUser(1L);
        when(progressAggregatorService.getLatestBatchId(1L)).thenReturn(7L);
        TrackProcessingProgressDTO dto = new TrackProcessingProgressDTO(7L, 2, 5, "0:10");
        when(progressAggregatorService.getProgress(7L)).thenReturn(dto);

        mockMvc.perform(get("/app/progress/latest")
                        .with(SecurityMockMvcRequestPostProcessors.user(user)))
                .andExpect(status().isOk())
                .andExpect(content().json(objectMapper.writeValueAsString(dto)));

        verify(progressAggregatorService).getLatestBatchId(1L);
        verify(progressAggregatorService).getProgress(7L);
    }

    /**
     * Анонимный пользователь получает пустой список результатов.
     */
    @Test
    void getLatestResults_Anonymous_ReturnsEmptyList() throws Exception {
        mockMvc.perform(get("/app/results/latest"))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));

        verifyNoInteractions(trackingResultCacheService);
    }

    /**
     * Проверяет выдачу списка последних результатов для пользователя.
     */
    @Test
    void getLatestResults_User_ReturnsListFromService() throws Exception {
        User user = buildUser(2L);
        List<TrackStatusUpdateDTO> list = List.of(new TrackStatusUpdateDTO(1L, "T123", "OK", 1, 1));
        when(trackingResultCacheService.getLatestResults(2L)).thenReturn(list);

        mockMvc.perform(get("/app/results/latest")
                        .with(SecurityMockMvcRequestPostProcessors.user(user)))
                .andExpect(status().isOk())
                .andExpect(content().json(objectMapper.writeValueAsString(list)));

        verify(trackingResultCacheService).getLatestResults(2L);
    }

    /**
     * Анонимный пользователь получает пустой список некорректных треков.
     */
    @Test
    void getLatestInvalid_Anonymous_ReturnsEmptyList() throws Exception {
        mockMvc.perform(get("/app/invalid/latest"))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));

        verifyNoInteractions(invalidTrackCacheService);
    }

    /**
     * Проверяет получение списка некорректных треков для пользователя.
     */
    @Test
    void getLatestInvalid_User_ReturnsListFromService() throws Exception {
        User user = buildUser(3L);
        List<InvalidTrack> list = List.of(new InvalidTrack("bad", InvalidTrackReason.WRONG_FORMAT));
        when(invalidTrackCacheService.getLatestInvalidTracks(3L)).thenReturn(list);

        mockMvc.perform(get("/app/invalid/latest")
                        .with(SecurityMockMvcRequestPostProcessors.user(user)))
                .andExpect(status().isOk())
                .andExpect(content().json(objectMapper.writeValueAsString(list)));

        verify(invalidTrackCacheService).getLatestInvalidTracks(3L);
    }

    /**
     * Очистка результатов должна вызывать сервис для авторизованного пользователя.
     */
    @Test
    void clearResults_User_InvokesService() throws Exception {
        User user = buildUser(4L);

        mockMvc.perform(post("/app/results/clear")
                        .with(SecurityMockMvcRequestPostProcessors.user(user)))
                .andExpect(status().isOk())
                .andExpect(content().string("cleared"));

        verify(trackingResultCacheService).clearResults(4L);
    }

    /**
     * Анонимный пользователь не должен вызывать очистку результатов.
     */
    @Test
    void clearResults_Anonymous_DoesNotInvokeService() throws Exception {
        mockMvc.perform(post("/app/results/clear"))
                .andExpect(status().isOk())
                .andExpect(content().string("cleared"));

        verifyNoInteractions(trackingResultCacheService);
    }

    /**
     * Проверяет очистку некорректных треков авторизованным пользователем.
     */
    @Test
    void clearInvalid_User_InvokesService() throws Exception {
        User user = buildUser(5L);

        mockMvc.perform(post("/app/invalid/clear")
                        .with(SecurityMockMvcRequestPostProcessors.user(user)))
                .andExpect(status().isOk())
                .andExpect(content().string("cleared"));

        verify(invalidTrackCacheService).clearInvalidTracks(5L);
    }

    /**
     * Анонимный пользователь не должен вызывать очистку некорректных треков.
     */
    @Test
    void clearInvalid_Anonymous_DoesNotInvokeService() throws Exception {
        mockMvc.perform(post("/app/invalid/clear"))
                .andExpect(status().isOk())
                .andExpect(content().string("cleared"));

        verifyNoInteractions(invalidTrackCacheService);
    }

    /**
     * Вспомогательный метод для создания сущности пользователя.
     *
     * @param id идентификатор пользователя
     * @return пользователь со стандартными полями
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

