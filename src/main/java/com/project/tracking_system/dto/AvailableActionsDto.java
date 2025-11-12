package com.project.tracking_system.dto;

import java.util.List;

/**
 * Обёртка над списком кодов доступных действий с заявкой на возврат/обмен.
 */
public final class AvailableActionsDto {

    private final List<String> actions;

    /**
     * Создаёт DTO доступных действий, гарантируя неизменяемость списка.
     *
     * @param actions коды доступных действий; {@code null} заменяется на пустой список
     */
    public AvailableActionsDto(List<String> actions) {
        this.actions = actions == null ? List.of() : List.copyOf(actions);
    }

    /**
     * Возвращает неизменяемый список кодов доступных действий.
     * Метод предоставляет фронтенду единый источник прав, удовлетворяя SRP.
     *
     * @return список action-code, всегда отличающийся null
     */
    public List<String> getActions() {
        return actions;
    }
}
