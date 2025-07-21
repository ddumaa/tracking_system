package com.project.tracking_system.service.belpost;

/**
 * Модель элемента очереди для пакетной обработки треков Белпочты.
 *
 * @param trackNumber номер отправления
 * @param userId      идентификатор пользователя
 * @param storeId     идентификатор магазина
 * @param source      источник поступления трека (ручной ввод, файл и т.п.)
 * @param batchId     идентификатор партии, объединяющей несколько треков
 */
public record QueuedTrack(String trackNumber,
                          Long userId,
                          Long storeId,
                          String source,
                          Long batchId) {
}
