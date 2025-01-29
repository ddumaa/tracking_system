package com.project.tracking_system.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @author Dmitriy Anisimov
 * @date 26.01.2025
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class EvropostCredentialsDTO {

    @NotBlank(message = "Имя пользователя обязательно")
    private String evropostUsername;

    @NotBlank(message = "Пароль обязателен")
    private String evropostPassword;

    @Pattern(
            regexp = "^[0-9A-Fa-f]{8}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{12}$",
            message = "Номер сервиса должен быть в формате UUID (например, 4D1B0EBC-FDCA-4445-A467-58CC5BE29D43)"
    )
    private String serviceNumber;

    private Boolean useCustomCredentials;

}