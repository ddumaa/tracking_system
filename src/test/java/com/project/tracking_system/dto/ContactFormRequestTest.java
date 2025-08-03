package com.project.tracking_system.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Тесты валидации {@link ContactFormRequest}.
 */
class ContactFormRequestTest {

    private static Validator validator;

    @BeforeAll
    static void setupValidator() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    /**
     * Проверяет, что пустые и некорректные поля вызывают ошибки валидации.
     */
    @Test
    void shouldFailValidationWhenFieldsInvalid() {
        ContactFormRequest request = new ContactFormRequest("", "invalid", "");
        Set<ConstraintViolation<ContactFormRequest>> violations = validator.validate(request);
        assertEquals(3, violations.size(), "Ожидалось 3 ошибки валидации");
    }

    /**
     * Проверяет, что корректно заполненная форма проходит валидацию.
     */
    @Test
    void shouldPassValidationWithCorrectData() {
        ContactFormRequest request = new ContactFormRequest("Иван", "test@example.com", "Сообщение");
        Set<ConstraintViolation<ContactFormRequest>> violations = validator.validate(request);
        assertTrue(violations.isEmpty(), "Не должно быть ошибок валидации");
    }
}
