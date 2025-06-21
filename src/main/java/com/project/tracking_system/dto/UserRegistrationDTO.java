package com.project.tracking_system.dto;

import jakarta.persistence.Column;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
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
public class UserRegistrationDTO {

    @Column(unique = true)
    @NotBlank(message = "Введите адрес электронной почты")
    private String email;

    @NotEmpty(message = "Пароль не может быть пустым")
    @Size(min = 6, max = 15, message = "Пароль должен быть не менее 6 символов и не более 15")
    private String password;

    @NotEmpty(message = "Заполните поле - 'Подтверждение пароля' ")
    private String confirmPassword;

    private String confirmCodRegistration;

    @AssertTrue(message = "Вы должны ознакомится и согласиться с Пользовательским соглашением")
    private boolean agreeToTerms;

}