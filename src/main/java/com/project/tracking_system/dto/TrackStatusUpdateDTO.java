package com.project.tracking_system.dto;

/**
 * Сообщение о текущем статусе обработки трека Белпочты.
 * <p>
 * Отправляется после обработки каждого трек-номера для обновления
 * прогресса у пользователя в реальном времени.
 * </p>
 *
 * @param batchId        идентификатор партии
 * @param trackingNumber номер трека
 * @param status         последний известный статус трека
 * @param completed      сколько треков уже обработано
 * @param total          общее количество треков в партии
 */
public record TrackStatusUpdateDTO(long batchId,
                                   String trackingNumber,
                                   String status,
                                   int completed,
                                   int total) {
}
