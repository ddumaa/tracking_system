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
 * @param attempt     номер текущей попытки обработки (начинается с нуля)
 */
public record QueuedTrack(String trackNumber,
                          Long userId,
                          Long storeId,
                          TrackSource source,
                          Long batchId,
                          String phone,
                          int attempt) {

    /**
     * Создаёт элемент очереди с нулевой попыткой обработки.
     *
     * @param trackNumber номер отправления
     * @param userId      идентификатор пользователя
     * @param storeId     идентификатор магазина
     * @param source      источник добавления трека
     * @param batchId     идентификатор партии
     * @param phone       номер телефона получателя
     */
    public QueuedTrack(String trackNumber,
                       Long userId,
                       Long storeId,
                       TrackSource source,
                       Long batchId,
                       String phone) {
        this(trackNumber, userId, storeId, source, batchId, phone, 0);
    }

    /**
     * Основной конструктор записи с проверкой корректности номера попытки.
     *
     * @param trackNumber номер отправления
     * @param userId      идентификатор пользователя
     * @param storeId     идентификатор магазина
     * @param source      источник добавления трека
     * @param batchId     идентификатор партии
     * @param phone       номер телефона получателя
     * @param attempt     номер текущей попытки обработки (не может быть отрицательным)
     */
    public QueuedTrack {
        if (attempt < 0) {
            throw new IllegalArgumentException("Номер попытки не может быть отрицательным");
        }
    }

    /**
     * Возвращает новую запись с указанным номером попытки.
     *
     * @param nextAttempt номер следующей попытки
     * @return копия текущего задания с обновлённым номером попытки
     */
    public QueuedTrack withAttempt(int nextAttempt) {
        return new QueuedTrack(trackNumber, userId, storeId, source, batchId, phone, nextAttempt);
    }

    /**
     * Создаёт копию задания, увеличивая номер попытки на единицу.
     *
     * @return новая запись с инкрементированным числом попыток
     */
    public QueuedTrack nextAttempt() {
        return withAttempt(attempt + 1);
    }
}