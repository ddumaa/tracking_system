package com.project.tracking_system.dto;

/**
 * Отдельное событие истории трека.
 *
 * @param status    текстовое описание статуса
 * @param timestamp отметка времени события в формате ISO_OFFSET_DATE_TIME
 */
public record TrackStatusEventDto(String status, String timestamp) {
}

