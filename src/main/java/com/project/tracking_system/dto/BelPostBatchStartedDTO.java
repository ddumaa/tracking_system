package com.project.tracking_system.dto;

/**
 * Сообщение о начале обработки партии треков Белпочты.
 *
 * @param batchId   идентификатор партии
 * @param totalCount количество треков в партии
 */
public record BelPostBatchStartedDTO(long batchId, int totalCount) {
}
