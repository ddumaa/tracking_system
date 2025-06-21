package com.project.tracking_system.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
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


    private boolean remindersEnabled = false;
}
