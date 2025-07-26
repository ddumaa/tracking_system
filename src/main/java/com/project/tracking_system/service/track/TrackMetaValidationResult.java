package com.project.tracking_system.service.track;

import java.util.List;

/**
 * Результат валидации и нормализации треков.
 * <p>
 * Содержит две выборки: корректные треки для дальнейшей обработки
 * и список {@link InvalidTrack}, описывающих некорректные строки.
 * При превышении пользовательских лимитов заполняется
 * {@code limitExceededMessage} с текстом предупреждения.
 * </p>
 *
 * @param validTracks           список успешно прошедших проверку треков
 * @param invalidTracks         собранные некорректные строки из исходного файла
 * @param limitExceededMessage  сообщение о превышении лимитов (может быть {@code null})
 */
public record TrackMetaValidationResult(List<TrackMeta> validTracks,
                                        List<InvalidTrack> invalidTracks,
                                        String limitExceededMessage) {
}
