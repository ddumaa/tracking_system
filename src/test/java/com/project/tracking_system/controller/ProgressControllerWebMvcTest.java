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
 * Используется {@link WebMvcTest} без фильтров безопасности, чтобы проверить
 * корректную работу контроллера в изоляции от остальных слоёв приложения.
 * </p>
 */
@ExtendWith(SpringExtension.class)
@WebMvcTest(ProgressController.class)
@AutoConfigureMockMvc(addFilters = false)
class ProgressControllerWebMvcTest {

    /** Мок для выполнения HTTP-запросов к контроллеру. */
    @Autowired
    private MockMvc mockMvc;

    /** Заглушка агрегатора прогресса. */
    @MockBean
    private ProgressAggregatorService progressAggregatorService;

    /** Заглушка кэша последних результатов. */
    @MockBean
    private TrackingResultCacheService trackingResultCacheService;

    /** Заглушка кэша некорректных треков. */
    @MockBean
    private InvalidTrackCacheService invalidTrackCacheService;

    /** Сериализатор JSON для сравнений. */
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Проверяет получение прогресса конкретного батча по его идентификатору.
     */
    @Test
    void getProgress_ReturnsDtoFromService() throws Exception {
        TrackProcessingProgressDTO dto = new TrackProcessingProgressDTO(5L, 2, 3, "0:05");
        when(progressAggregatorService.getProgress(5L)).thenReturn(dto);

        mockMvc.perform(MockMvcRequestBuilders.get("/app/progress/5"))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.content().json(objectMapper.writeValueAsString(dto)));

        verify(progressAggregatorService).getProgress(5L);
    }

    /**
     * Убеждаемся, что анонимный пользователь получает пустой прогресс
     * без обращения к сервису.
     */
    @Test
    void getLatestProgress_Anonymous_ReturnsEmptyDto() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/app/progress/latest"))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.batchId").value(0))
                .andExpect(MockMvcResultMatchers.jsonPath("$.processed").value(0))
                .andExpect(MockMvcResultMatchers.jsonPath("$.total").value(0))
                .andExpect(MockMvcResultMatchers.jsonPath("$.elapsed").value("0:00"));

        verifyNoInteractions(progressAggregatorService);
    }

    /**
     * Проверяет возврат актуальных данных прогресса для аутентифицированного пользователя.
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
     * Проверяет, что анонимный пользователь получает пустой список результатов.
     */
    @Test
    void getLatestResults_Anonymous_ReturnsEmptyList() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/app/results/latest"))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.content().json("[]"));

        verifyNoInteractions(trackingResultCacheService);
    }

    /**
     * Проверяет получение списка последних результатов для пользователя.
     */
    @Test
    void getLatestResults_User_ReturnsListFromService() throws Exception {
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
     * Проверяет, что анонимный пользователь получает пустой список некорректных треков.
     */
    @Test
    void getLatestInvalid_Anonymous_ReturnsEmptyList() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/app/invalid/latest"))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.content().json("[]"));

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

        mockMvc.perform(MockMvcRequestBuilders.get("/app/invalid/latest")
                        .with(SecurityMockMvcRequestPostProcessors.user(user)))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.content().json(objectMapper.writeValueAsString(list)));

        verify(invalidTrackCacheService).getLatestInvalidTracks(3L);
    }

    /**
     * Проверяет очистку результатов пользователем.
     */
    @Test
    void clearResults_User_InvokesService() throws Exception {
        User user = buildUser(2L);

        mockMvc.perform(MockMvcRequestBuilders.post("/app/results/clear")
                        .with(SecurityMockMvcRequestPostProcessors.user(user)))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.content().string("cleared"));

        verify(trackingResultCacheService).clearResults(2L);
    }

    /**
     * Проверяет, что анонимный пользователь не вызывает очистку результатов.
     */
    @Test
    void clearResults_Anonymous_DoesNotInvokeService() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.post("/app/results/clear"))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.content().string("cleared"));

        verifyNoInteractions(trackingResultCacheService);
    }

    /**
     * Проверяет очистку списка некорректных треков пользователем.
     */
    @Test
    void clearInvalid_User_InvokesService() throws Exception {
        User user = buildUser(4L);

        mockMvc.perform(MockMvcRequestBuilders.post("/app/invalid/clear")
                        .with(SecurityMockMvcRequestPostProcessors.user(user)))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.content().string("cleared"));

        verify(invalidTrackCacheService).clearInvalidTracks(4L);
    }

    /**
     * Проверяет, что анонимный пользователь не вызывает очистку некорректных треков.
     */
    @Test
    void clearInvalid_Anonymous_DoesNotInvokeService() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.post("/app/invalid/clear"))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.content().string("cleared"));

        verifyNoInteractions(invalidTrackCacheService);
    }

    /**
     * Создаёт сущность пользователя для тестов.
     *
     * @param id идентификатор пользователя
     * @return настроенный пользователь
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

