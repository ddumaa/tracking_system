package com.project.tracking_system.service.track;

import com.project.tracking_system.dto.TrackingResultAdd;
import com.project.tracking_system.entity.PostalServiceType;

import java.util.List;

/**
 * Контракт обработчика обновлений для конкретной почтовой службы.
 */
public interface TrackUpdateProcessor {

    /**
     * @return тип почтовой службы, которую обрабатывает процессор
     */
    PostalServiceType supportedType();

    /**
     * Обрабатывает переданные треки.
     *
     * @param tracks список треков
     * @param userId идентификатор пользователя, может быть {@code null}
     * @return список результатов обработки
     */
    List<TrackingResultAdd> process(List<TrackMeta> tracks, Long userId);

    /**
     * Обрабатывает один трек.
     *
     * @param meta   метаданные трека
     * @param userId идентификатор пользователя, может быть {@code null}
     * @return результат обработки
     */
    TrackingResultAdd process(TrackMeta meta, Long userId);
}