package com.project.tracking_system.service.belpost;

import com.project.tracking_system.service.track.TrackSource;

/**
 * Модель элемента очереди для пакетной обработки треков Белпочты.
 *
 * @param trackNumber номер отправления
 * @param userId      идентификатор пользователя
 * @param storeId     идентификатор магазина
 * @param source      источник поступления трека
 *                    (см. {@link TrackSource})
 * @param batchId     идентификатор партии, объединяющей несколько треков
 */
public record QueuedTrack(String trackNumber,
                          Long userId,
                          Long storeId,
                          TrackSource source,
                          Long batchId) {
}
