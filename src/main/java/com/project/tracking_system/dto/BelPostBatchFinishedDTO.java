package com.project.tracking_system.dto;

/**
 * Сообщение о завершении обработки партии треков Белпочты.
 *
 * @param batchId   идентификатор партии
 * @param processed обработано треков
 * @param success   количество успешных
 * @param failed    количество неуспешных
 */
public record BelPostBatchFinishedDTO(long batchId,
                                      int processed,
                                      int success,
                                      int failed) {
}
