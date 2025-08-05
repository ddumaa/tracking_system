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
import org.junit.jupiter.api.Nested;
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

import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Интеграционные тесты REST-эндпоинтов {@link ProgressController}.
 * <p>
 * Класс использует {@link WebMvcTest} без фильтров безопасности для проверки
 * работы контроллера в изоляции.
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

    @Nested
    class GetProgress {

        /**
         * Проверяет получение прогресса по идентификатору батча.
         */
        @Test
        void returnsDtoFromService() throws Exception {
            TrackProcessingProgressDTO dto = new TrackProcessingProgressDTO(5L, 2, 3, "0:05");
            when(progressAggregatorService.getProgress(5L)).thenReturn(dto);

            mockMvc.perform(MockMvcRequestBuilders.get("/app/progress/5"))
                    .andExpect(status().isOk())
                    .andExpect(content().json(objectMapper.writeValueAsString(dto)));

            verify(progressAggregatorService).getProgress(5L);
        }
    }

    @Nested
    class GetLatestProgress {

        /**
         * Убеждаемся, что аноним получает пустой прогресс.
         */
        @Test
        void anonymousReceivesEmptyDto() throws Exception {
            mockMvc.perform(MockMvcRequestBuilders.get("/app/progress/latest"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.batchId").value(0))
                    .andExpect(jsonPath("$.processed").value(0))
                    .andExpect(jsonPath("$.total").value(0))
                    .andExpect(jsonPath("$.elapsed").value("0:00"));

            verifyNoInteractions(progressAggregatorService);
        }

        /**
         * Проверяет получение актуального прогресса для пользователя.
         */
        @Test
        void userReceivesDtoFromService() throws Exception {
            User user = buildUser(1L);
            when(progressAggregatorService.getLatestBatchId(1L)).thenReturn(7L);
            TrackProcessingProgressDTO dto = new TrackProcessingProgressDTO(7L, 2, 5, "0:10");
            when(progressAggregatorService.getProgress(7L)).thenReturn(dto);

            mockMvc.perform(MockMvcRequestBuilders.get("/app/progress/latest")
                            .with(SecurityMockMvcRequestPostProcessors.user(user)))
                    .andExpect(status().isOk())
                    .andExpect(content().json(objectMapper.writeValueAsString(dto)));

            verify(progressAggregatorService).getLatestBatchId(1L);
            verify(progressAggregatorService).getProgress(7L);
        }
    }

    @Nested
    class GetLatestResults {

        /**
         * Проверяет, что аноним получает пустой список.
         */
        @Test
        void anonymousReceivesEmptyList() throws Exception {
            mockMvc.perform(MockMvcRequestBuilders.get("/app/results/latest"))
                    .andExpect(status().isOk())
                    .andExpect(content().json("[]"));

            verifyNoInteractions(trackingResultCacheService);
        }

        /**
         * Возвращает список последних результатов для пользователя.
         */
        @Test
        void userReceivesListFromService() throws Exception {
            User user = buildUser(1L);
            List<TrackStatusUpdateDTO> list = List.of(new TrackStatusUpdateDTO(1L, "T123", "OK", 1, 1));
            when(trackingResultCacheService.getLatestResults(1L)).thenReturn(list);

            mockMvc.perform(MockMvcRequestBuilders.get("/app/results/latest")
                            .with(SecurityMockMvcRequestPostProcessors.user(user)))
                    .andExpect(status().isOk())
                    .andExpect(content().json(objectMapper.writeValueAsString(list)));

            verify(trackingResultCacheService).getLatestResults(1L);
        }
    }

    @Nested
    class GetLatestInvalid {

        /**
         * Анонимный пользователь получает пустой список некорректных треков.
         */
        @Test
        void anonymousReceivesEmptyList() throws Exception {
            mockMvc.perform(MockMvcRequestBuilders.get("/app/invalid/latest"))
                    .andExpect(status().isOk())
                    .andExpect(content().json("[]"));

            verifyNoInteractions(invalidTrackCacheService);
        }

        /**
         * Пользователь получает список некорректных треков из сервиса.
         */
        @Test
        void userReceivesListFromService() throws Exception {
            User user = buildUser(3L);
            List<InvalidTrack> list = List.of(new InvalidTrack("bad", InvalidTrackReason.WRONG_FORMAT));
            when(invalidTrackCacheService.getLatestInvalidTracks(3L)).thenReturn(list);

            mockMvc.perform(MockMvcRequestBuilders.get("/app/invalid/latest")
                            .with(SecurityMockMvcRequestPostProcessors.user(user)))
                    .andExpect(status().isOk())
                    .andExpect(content().json(objectMapper.writeValueAsString(list)));

            verify(invalidTrackCacheService).getLatestInvalidTracks(3L);
        }
    }

    @Nested
    class ClearResults {

        /**
         * Проверяет очистку результатов пользователем.
         */
        @Test
        void userClearsResults() throws Exception {
            User user = buildUser(2L);

            mockMvc.perform(MockMvcRequestBuilders.post("/app/results/clear")
                            .with(SecurityMockMvcRequestPostProcessors.user(user)))
                    .andExpect(status().isOk())
                    .andExpect(content().string("cleared"));

            verify(trackingResultCacheService).clearResults(2L);
        }

        /**
         * Анонимный пользователь не вызывает сервис очистки.
         */
        @Test
        void anonymousDoesNotInvokeService() throws Exception {
            mockMvc.perform(MockMvcRequestBuilders.post("/app/results/clear"))
                    .andExpect(status().isOk())
                    .andExpect(content().string("cleared"));

            verifyNoInteractions(trackingResultCacheService);
        }
    }

    @Nested
    class ClearInvalid {

        /**
         * Пользователь очищает список некорректных треков.
         */
        @Test
        void userClearsInvalidTracks() throws Exception {
            User user = buildUser(4L);

            mockMvc.perform(MockMvcRequestBuilders.post("/app/invalid/clear")
                            .with(SecurityMockMvcRequestPostProcessors.user(user)))
                    .andExpect(status().isOk())
                    .andExpect(content().string("cleared"));

            verify(invalidTrackCacheService).clearInvalidTracks(4L);
        }

        /**
         * Анонимный пользователь не вызывает сервис очистки.
         */
        @Test
        void anonymousDoesNotInvokeService() throws Exception {
            mockMvc.perform(MockMvcRequestBuilders.post("/app/invalid/clear"))
                    .andExpect(status().isOk())
                    .andExpect(content().string("cleared"));

            verifyNoInteractions(invalidTrackCacheService);
        }
    }

    /**
     * Создаёт объект пользователя для тестов.
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

