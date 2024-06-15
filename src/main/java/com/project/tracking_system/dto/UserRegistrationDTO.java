package com.project.tracking_system.dto;

import jakarta.persistence.Column;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@AllArgsConstructor
@Data
public class UserRegistrationDTO {

    @Column(unique = true)
    @NotBlank(message = "Введите имя пользователя")
    private String username;

    @NotEmpty(message = "Пароль не может быть пустым")
    @Size(min = 6, max = 15, message = "Пароль должен быть не менее 6 символов и не более 15")
    private String password;

    @NotEmpty(message = "Введите пароль ещё раз")
    private String confirmPassword;

}
