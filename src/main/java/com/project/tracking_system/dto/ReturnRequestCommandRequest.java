package com.project.tracking_system.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Запрос на выполнение команды с заявкой возврата/обмена.
 *
 * @param idempotencyKey     ключ идемпотентности для защиты от повторов
 * @param command            строковый код команды
 * @param reverseTrackNumber новый обратный трек (для команд обновления деталей)
 * @param comment            дополнительный комментарий
 */
public record ReturnRequestCommandRequest(@NotBlank String idempotencyKey,
                                          @NotBlank String command,
                                          String reverseTrackNumber,
                                          String comment) {
}
