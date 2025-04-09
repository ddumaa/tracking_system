package com.project.tracking_system.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @author Dmitriy Anisimov
 * @date 21.03.2025
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserProfileDTO {

    private String email; // отображение e-mail, но не редактируемый
    //private String fullName; // опционально, если в будущем понадобится
    private String timezone; // например, "Europe/Minsk"

}