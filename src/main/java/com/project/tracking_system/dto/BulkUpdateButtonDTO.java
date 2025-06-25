package com.project.tracking_system.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * DTO для флага отображения кнопки массового обновления.
 * <p>
 * Передаётся из контроллера отправлений в представление,
 * чтобы определить необходимость показа кнопки.
 * </p>
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class BulkUpdateButtonDTO {

    /**
     * Показывать ли кнопку массового обновления.
     */
    private Boolean showBulkUpdateButton;
}
