package com.project.tracking_system.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO настроек Telegram для магазина.
 */
@Data
@NoArgsConstructor
public class StoreTelegramSettingsDTO {
    private boolean enabled = true;
    private int reminderStartAfterDays = 3;
    private int reminderRepeatIntervalDays = 2;
    private String customSignature;
}
