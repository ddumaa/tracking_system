package com.project.tracking_system.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Запрос на изменение трек-номера.
 *
 * @param number новое значение трек-номера
 */
public record TrackNumberUpdateRequest(@NotBlank(message = "Трек-номер обязателен") String number) {
}
