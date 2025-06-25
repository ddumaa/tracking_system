package com.project.tracking_system.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Лёгкое представление магазина для профиля.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class StoreDTO {
    private Long id;
    private String name;
    /** Признак магазина по умолчанию. */
    private boolean isDefault;
    /** Настройки Telegram данного магазина. */
    private StoreTelegramSettingsDTO telegramSettings;
}
