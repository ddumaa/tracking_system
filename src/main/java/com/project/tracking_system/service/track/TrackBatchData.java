package com.project.tracking_system.service.track;

import com.project.tracking_system.entity.PostalServiceType;

import java.util.List;
import java.util.Map;

/**
 * Результат чтения файла с трек-номерами.
 * <p>
 * Содержит сгруппированные метаданные треков и сообщение о превышении лимитов.
 * </p>
 *
 * @param tracksByService     отображение "почтовая служба → список метаданных"
 * @param limitExceededMessage сообщение о превышении лимита (может быть {@code null})
 */
public record TrackBatchData(Map<PostalServiceType, List<TrackMeta>> tracksByService,
                             String limitExceededMessage) {
}
