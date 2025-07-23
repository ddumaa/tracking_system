package com.project.tracking_system.controller;

import com.project.tracking_system.dto.TrackProcessingProgressDTO;
import com.project.tracking_system.service.track.ProgressAggregatorService;
import com.project.tracking_system.utils.ResponseBuilder;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
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
}
