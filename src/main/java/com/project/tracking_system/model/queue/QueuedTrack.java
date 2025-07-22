package com.project.tracking_system.model.queue;

/**
 * Представляет трек, помещённый в очередь на обработку.
 *
 * @param number  номер посылки
 * @param store   значение магазина из исходного файла
 * @param phone   телефон получателя
 * @param userId  идентификатор пользователя, загрузившего трек
 * @param batchId идентификатор пачки, к которой принадлежит трек
 * @param source  источник загрузки (например, EXCEL)
 */
public record QueuedTrack(String number,
                          String store,
                          String phone,
                          Long userId,
                          String batchId,
                          String source) {
}
