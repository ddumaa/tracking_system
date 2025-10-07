package com.project.tracking_system.dto;

/**
 * Представляет этап жизненного цикла заказа в модальном окне трека.
 *
 * @param code         машинный код этапа для аналитики и тестов
 * @param title        человеко-читаемый заголовок этапа
 * @param actor        участник процесса (магазин или покупатель)
 * @param description  краткое описание действия на этапе
 * @param state        текущее состояние этапа
 * @param occurredAt   момент завершения или начала этапа в ISO-формате
 * @param trackNumber  трек-номер, относящийся к этапу (может отсутствовать)
 * @param trackContext короткая подпись, описывающая роль трека на этапе
 */
public record TrackLifecycleStageDto(String code,
                                     String title,
                                     String actor,
                                     String description,
                                     TrackLifecycleStageState state,
                                     String occurredAt,
                                     String trackNumber,
                                     String trackContext) {
}
