package com.project.tracking_system.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;

/**
 * Команда управления заявкой на возврат/обмен.
 *
 * @param idempotencyKey уникальный ключ команды
 * @param action         код действия
 * @param payload        полезная нагрузка с параметрами в свободном формате
 */
public record CommandDto(@NotBlank String idempotencyKey,
                         @NotBlank String action,
                         JsonNode payload) {
}
