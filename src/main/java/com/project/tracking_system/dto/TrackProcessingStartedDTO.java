package com.project.tracking_system.dto;

/**
 * Сообщение о начале пакетной обработки треков.
 *
 * @param totalCount количество треков в очереди
 * @param eta        предполагаемое время обработки всей партии
 * @param waitTime   примерное время ожидания до начала
 */
public record TrackProcessingStartedDTO(int totalCount, String eta, String waitTime) {
}
