package com.project.tracking_system.service.track;

import java.util.List;

/**
 * Результат валидации и нормализации треков.
 *
 * @param validTracks список корректных треков
 * @param invalidTracks список некорректных строк
 * @param limitExceededMessage сообщение о превышении лимитов (может быть null)
 */
public record TrackMetaValidationResult(List<TrackMeta> validTracks,
                                        List<InvalidTrack> invalidTracks,
                                        String limitExceededMessage) {
}
