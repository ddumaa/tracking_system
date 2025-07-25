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
/**
 * Представляет элемент очереди для поэтапной обработки трек-номеров Белпочты.
 *
 * @param trackNumber номер отправления
 * @param userId      идентификатор пользователя
 * @param storeId     идентификатор магазина
 * @param source      источник добавления трека {@link TrackSource}
 * @param batchId     идентификатор партии обработки
 * @param phone       номер телефона получателя (может быть {@code null})
 */
public record QueuedTrack(String trackNumber,
                          Long userId,
                          Long storeId,
                          TrackSource source,
                          Long batchId,
                          String phone) {
}
