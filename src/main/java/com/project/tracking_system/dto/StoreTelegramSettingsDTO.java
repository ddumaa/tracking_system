package com.project.tracking_system.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.util.HashMap;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

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

    @Size(max = 200)
    private String customSignature;

    /** Токен пользовательского Telegram-бота. */
    private String botToken;

    /** Имя пользовательского бота. */
    private String botUsername;


    private boolean remindersEnabled = false;

    /** Использовать пользовательские шаблоны сообщений. */
    private boolean useCustomTemplates = false;

    /** Статус → шаблон сообщения. */
    private Map<String, String> templates = new HashMap<>();

    /** Шаблон напоминания. */
    private String reminderTemplate;
}
