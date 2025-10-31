package com.project.tracking_system.dto;

import java.util.Objects;

/**
 * Описание доступного действия с заявкой на возврат/обмен.
 *
 * @param code            машинный код действия
 * @param label           человеко-читаемое название
 * @param enabled         признак доступности для выполнения
 * @param disabledReason  причина недоступности (может быть {@code null})
 */
public record AvailableActionsDto(String code,
                                  String label,
                                  boolean enabled,
                                  String disabledReason) {

    public AvailableActionsDto {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("Код действия не может быть пустым");
        }
        label = Objects.requireNonNullElse(label, code);
    }
}
