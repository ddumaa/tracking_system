package com.project.tracking_system.dto;

/**
 * Результат получения информации о треке для отображения.
 *
 * @param trackInfo      список статусов посылки
 * @param nextUpdateTime строка с датой и временем следующего допустимого обновления
 */
public record TrackViewResult(TrackInfoListDTO trackInfo, String nextUpdateTime) {
}
