package com.project.tracking_system.service.order;

import com.project.tracking_system.entity.TrackParcel;

import java.time.ZonedDateTime;

/**
 * Событие, зафиксированное в трекинге и влияющее на стадию заявки возврата.
 * <p>
 * Запись агрегирует тип события, посылку-источник и момент времени,
 * чтобы обработчик мог выбрать корректную бизнес-логику.
 * </p>
 */
public record ReturnTrackingEvent(ReturnTrackingEventType type,
                                  TrackParcel parcel,
                                  ZonedDateTime occurredAt) {
}
