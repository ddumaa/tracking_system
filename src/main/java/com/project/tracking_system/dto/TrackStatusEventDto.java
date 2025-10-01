package com.project.tracking_system.dto;

/**
 * Отдельное событие истории трека.
 *
 * @param status    краткое описание статуса, отображаемое крупным шрифтом в таймлайне
 * @param timestamp отметка времени события в формате ISO_OFFSET_DATE_TIME
 * @param details   детальное сообщение от почтовой службы (может отсутствовать)
 */
public record TrackStatusEventDto(String status, String timestamp, String details) {

    /**
     * Создаёт событие без детализированного текста.
     * Конструктор упрощает миграцию старого кода, где передавались только статус и время.
     */
    public TrackStatusEventDto(String status, String timestamp) {
        this(status, timestamp, null);
    }
}

