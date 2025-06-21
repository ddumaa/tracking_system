package com.project.tracking_system.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class PasswordResetDTO {

    @NotEmpty(message = "Поле 'Новый пароль' не должно быть пустым")
    @Size(min = 6, max = 15, message = "Пароль должен быть не менее 6 символов и не более 15")
    private String newPassword;

    @NotEmpty(message = "Поле 'Подтверждение пароля' не должно быть пустым")
    private String confirmPassword;

    public boolean passwordsMatch() {
        return this.newPassword.equals(this.confirmPassword);
    }
}
