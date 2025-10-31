package com.project.tracking_system.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

/**
 * Команда управления заявкой на возврат/обмен.
 *
 * @param idempotencyKey уникальный ключ команды
 * @param action         код действия
 * @param payload        полезная нагрузка с параметрами
 */
public record CommandDto(@NotBlank String idempotencyKey,
                         @NotBlank String action,
                         @Valid Payload payload) {

    /**
     * Полезная нагрузка команды, содержащая дополнительные параметры.
     *
     * @param reverseTrack обратный трек
     * @param comment      комментарий пользователя
     */
    public record Payload(String reverseTrack,
                          String comment) {
    }
}
