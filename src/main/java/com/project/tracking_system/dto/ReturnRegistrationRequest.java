package com.project.tracking_system.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Запрос на регистрацию заявки возврата/обмена.
 *
 * @param idempotencyKey идемпотентный ключ для защиты от повторов
 */
public record ReturnRegistrationRequest(@NotBlank(message = "Идемпотентный ключ обязателен") String idempotencyKey) {
}

