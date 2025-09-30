package com.project.tracking_system.dto;

/**
 * Ответ на изменение трек-номера.
 *
 * @param details обновлённые данные для модального окна
 * @param summary обновлённые данные для таблицы отправлений
 */
public record TrackNumberUpdateResponse(TrackDetailsDto details, TrackParcelDTO summary) {
}
