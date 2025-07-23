package com.project.tracking_system.dto;

/**
 * Данные о текущем прогрессе обработки партии треков.
 *
 * @param batchId   идентификатор партии
 * @param processed сколько треков уже обработано
 * @param total     общее количество треков
 * @param elapsed   время с начала обработки в формате mm:ss
 */
public record TrackProcessingProgressDTO(long batchId,
                                         int processed,
                                         int total,
                                         String elapsed) {
}
