package com.project.tracking_system.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.HashMap;
import java.util.Map;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * DTO настроек Telegram для магазина.
 */
@Getter
@Setter
@NoArgsConstructor
public class StoreTelegramSettingsDTO {
    private boolean enabled = true;
    @Min(1)
    @Max(14)
    private int reminderStartAfterDays = 3;

    @Min(1)
    @Max(14)
    private int reminderRepeatIntervalDays = 2;


    private String reminderTemplate;


    private boolean remindersEnabled = false;

    /**
     * Режим использования шаблонов уведомлений.
     * <p>
     * Возможные значения: {@code system} или {@code custom}. По умолчанию
     * используется {@code system}.
     * </p>
     */
    private String useCustomTemplates = "system";

    /**
     * Проверяет, активирован ли режим собственных шаблонов.
     *
     * @return {@code true}, если выбран вариант {@code custom}
     */
    public boolean isCustomTemplates() {
        return "custom".equalsIgnoreCase(useCustomTemplates);
    }

    /**
     * Устанавливает режим шаблонов по булеву значению.
     *
     * @param enabled {@code true} для режима {@code custom}, иначе {@code system}
     */
    public void setCustomTemplates(boolean enabled) {
        this.useCustomTemplates = enabled ? "custom" : "system";
    }

    /** Статус → шаблон сообщения. */
    private Map<String, String> templates = new HashMap<>();
}
