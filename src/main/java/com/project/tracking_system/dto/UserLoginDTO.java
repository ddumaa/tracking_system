package com.project.tracking_system.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@NoArgsConstructor
@AllArgsConstructor
@Data
public class UserLoginDTO {

    @NotBlank(message = "Введите адрес электронной почты")
    private String username;

    @NotEmpty(message = "Введите пароль")
    private String password;

}
