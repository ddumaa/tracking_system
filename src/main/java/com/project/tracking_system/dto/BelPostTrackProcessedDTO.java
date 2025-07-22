package com.project.tracking_system.dto;

/**
 * Сообщение о результатах обработки одного трека Белпочты.
 *
 * @param batchId    идентификатор партии
 * @param trackNumber номер трека
 * @param processed  сколько треков обработано
 * @param success    количество успешных
 * @param failed     количество неуспешных
 */
public record BelPostTrackProcessedDTO(long batchId,
                                       String trackNumber,
                                       int processed,
                                       int success,
                                       int failed) {
}
