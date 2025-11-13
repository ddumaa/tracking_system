package com.project.tracking_system.dto;

import java.util.List;

/**
 * Обёртка над списком кодов доступных действий с заявкой на возврат/обмен.
 * <p>
 * DTO содержит только необходимый для JSON Schema список action-code, чтобы не
 * раскрывать дополнительные флаги или причины недоступности. Класс остаётся
 * неизменяемым и соответствует принципу единой ответственности, предоставляя
 * только данные о действиях.
 * </p>
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
     * Возвращает список кодов доступных действий.
     *
     * @return неизменяемый список кодов действий
     */
    public List<String> getActions() {
        return actions;
    }
}
