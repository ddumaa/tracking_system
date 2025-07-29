package com.project.tracking_system.dto;

/**
 * Сообщение о завершении обработки партии треков Белпочты.
 *
 * @param batchId   идентификатор партии
 * @param processed обработано треков
 * @param success   количество успешных
 * @param failed    количество неуспешных
 * @param elapsed   время обработки партии в формате mm:ss
 */
public record BelPostBatchFinishedDTO(long batchId,
                                      int processed,
                                      int success,
                                      int failed,
                                      String elapsed) {
}
