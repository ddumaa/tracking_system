package com.project.tracking_system.dto;

/**
 * DTO стандартного ответа с ошибкой для REST-клиентов.
 * <p>
 * Содержит унифицированный код и текстовое описание, которое можно безопасно
 * отображать пользователю без дополнительной обработки на клиенте.
 * </p>
 *
 * @param code    тип ошибки, определяющий дальнейшие действия клиента
 * @param message человеко-читаемое описание проблемы
 */
public record ErrorResponseDto(ReturnApiErrorCode code, String message) {
}
