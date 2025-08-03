package com.project.tracking_system.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * DTO для передачи данных формы обратной связи.
 * <p>
 * Содержит имя, email и текст сообщения пользователя.
 * Валидируется при получении в контроллере.
 * </p>
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ContactFormRequest {

    /** Имя отправителя сообщения. */
    @NotBlank(message = "Введите имя")
    @Size(max = 100, message = "Имя должно быть не длиннее 100 символов")
    private String name;

    /** Электронный адрес отправителя. */
    @NotBlank(message = "Введите адрес электронной почты")
    @Email(message = "Некорректный адрес электронной почты")
    @Size(max = 100, message = "Email должен быть не длиннее 100 символов")
    private String email;

    /** Текст сообщения пользователя. */
    @NotBlank(message = "Введите текст сообщения")
    @Size(max = 1000, message = "Сообщение должно быть не длиннее 1000 символов")
    private String message;
}
