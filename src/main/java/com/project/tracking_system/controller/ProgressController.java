package com.project.tracking_system.controller;

import com.project.tracking_system.dto.TrackProcessingProgressDTO;
import com.project.tracking_system.service.track.ProgressAggregatorService;
import com.project.tracking_system.utils.ResponseBuilder;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import com.project.tracking_system.entity.User;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST-контроллер для получения прогресса обработки треков.
 */
@RequiredArgsConstructor
@RestController
public class ProgressController {

    private final ProgressAggregatorService progressAggregatorService;

    /**
     * Возвращает актуальный прогресс обработки партии.
     *
     * @param batchId идентификатор партии
     * @return прогресс в виде {@link TrackProcessingProgressDTO}
     */
    @GetMapping("/app/progress/{batchId}")
    public ResponseEntity<TrackProcessingProgressDTO> getProgress(@PathVariable Long batchId) {
        return ResponseBuilder.ok(progressAggregatorService.getProgress(batchId));
    }

    /**
     * Возвращает прогресс последней активной партии текущего пользователя.
     * <p>
     * Если активных партий нет, возвращает пустой прогресс с total = 0.
     * </p>
     *
     * @param user аутентифицированный пользователь
     * @return прогресс в виде {@link TrackProcessingProgressDTO}
     */
    @GetMapping("/app/progress/latest")
    public ResponseEntity<TrackProcessingProgressDTO> getLatestProgress(@AuthenticationPrincipal User user) {
        if (user == null) {
            return ResponseBuilder.ok(new TrackProcessingProgressDTO(0L, 0, 0, "0:00"));
        }
        Long batchId = progressAggregatorService.getLatestBatchId(user.getId());
        TrackProcessingProgressDTO dto = batchId != null
                ? progressAggregatorService.getProgress(batchId)
                : new TrackProcessingProgressDTO(0L, 0, 0, "0:00");
        return ResponseBuilder.ok(dto);
    }
}
