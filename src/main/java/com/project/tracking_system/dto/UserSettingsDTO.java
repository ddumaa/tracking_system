package com.project.tracking_system.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class UserSettingsDTO {

    @NotEmpty(message = "Текущий пароль обязателен")
    private String currentPassword;

    @NotEmpty(message = "Новый пароль обязателен")
    @Size(min = 6, max = 15, message = "Пароль должен быть не менее 6 символов и не более 15")
    private String newPassword;

    @NotEmpty(message = "Подтверждение пароля обязательно")
    private String confirmPassword;

}
