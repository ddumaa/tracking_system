package com.project.tracking_system.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * DTO общих настроек пользователя.
 * <p>
 * Используется для отображения/изменения различных флагов в профиле.
 * Поля для смены пароля вынесены в {@link PasswordChangeDTO}.
 * </p>
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UserSettingsDTO {

    /**
     * Показывать кнопку массового обновления треков.
     */
    private Boolean showBulkUpdateButton;

    /**
     * Разрешены ли уведомления Telegram для всех магазинов.
     */
    private Boolean telegramNotificationsEnabled;
}
