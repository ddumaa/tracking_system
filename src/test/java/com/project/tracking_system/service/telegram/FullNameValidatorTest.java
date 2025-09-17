package com.project.tracking_system.service.telegram;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Тесты проверяют правила валидации и нормализации ФИО для Telegram-бота.
 */
class FullNameValidatorTest {

    private final FullNameValidator validator = new FullNameValidator();

    /**
     * Убеждается, что валидатор нормализует регистр и лишние пробелы.
     */
    @Test
    void shouldNormalizeWhitespaceAndCase() {
        FullNameValidator.FullNameValidationResult result = validator.validate("  иВАН   иваНов-сидОРов  ");

        assertTrue(result.valid(), "Ожидалась успешная валидация ФИО");
        assertEquals("Иван Иванов-Сидоров", result.normalizedFullName());
    }

    /**
     * Проверяет, что слишком короткое ФИО отклоняется.
     */
    @Test
    void shouldRejectTooShortName() {
        FullNameValidator.FullNameValidationResult result = validator.validate("И");

        assertFalse(result.valid());
        assertEquals(FullNameValidator.FullNameValidationError.TOO_SHORT, result.error());
        assertTrue(result.message().contains("не менее"));
    }

    /**
     * Убеждается, что в имени не допускаются цифры и спецсимволы.
     */
    @Test
    void shouldRejectInvalidCharacters() {
        FullNameValidator.FullNameValidationResult result = validator.validate("Иван123");

        assertFalse(result.valid());
        assertEquals(FullNameValidator.FullNameValidationError.INVALID_SYMBOLS, result.error());
        assertTrue(result.message().contains("буквы"));
    }

    /**
     * Проверяет, что одно слово без фамилии отклоняется.
     */
    @Test
    void shouldRejectSingleWordFullName() {
        FullNameValidator.FullNameValidationResult result = validator.validate("Иван");

        assertFalse(result.valid());
        assertEquals(FullNameValidator.FullNameValidationError.NAME_AND_SURNAME_REQUIRED, result.error());
        assertTrue(result.message().contains("имя и фамилию"));
    }

    /**
     * Проверяет, что при пустом вводе возвращается подсказка с примером полного ФИО.
     */
    @Test
    void shouldRequestFullNameExampleWhenEmpty() {
        FullNameValidator.FullNameValidationResult result = validator.validate("   ");

        assertFalse(result.valid());
        assertEquals(FullNameValidator.FullNameValidationError.EMPTY, result.error());
        assertTrue(result.message().contains("Иванов Иван Иванович"));
    }

    /**
     * Проверяет, что подтверждающие слова попадают в чёрный список.
     */
    @Test
    void shouldRejectConfirmationPhrase() {
        FullNameValidator.FullNameValidationResult result = validator.validate("ДА");

        assertFalse(result.valid());
        assertEquals(FullNameValidator.FullNameValidationError.CONFIRMATION_PHRASE, result.error());
        assertTrue(result.message().contains("подтверждения"));
    }

    /**
     * Убеждается, что метод распознаёт подтверждающие слова вне зависимости от регистра и пробелов.
     */
    @Test
    void shouldRecognizeConfirmationPhraseIgnoringCase() {
        assertTrue(validator.isConfirmationPhrase("  оК  "));
        assertFalse(validator.isConfirmationPhrase("Мария"));
    }

    /**
     * Проверяет ограничение на максимальную длину ФИО.
     */
    @Test
    void shouldRejectTooLongName() {
        String veryLong = "А".repeat(FullNameValidator.MAX_LENGTH + 1);
        FullNameValidator.FullNameValidationResult result = validator.validate(veryLong);

        assertFalse(result.valid());
        assertEquals(FullNameValidator.FullNameValidationError.TOO_LONG, result.error());
        assertTrue(result.message().contains("не более"));
    }

    /**
     * Убеждается, что корректные варианты ФИО принимаются валидатором.
     */
    @Test
    void shouldAcceptValidFullNames() {
        FullNameValidator.FullNameValidationResult regular = validator.validate("мария   иВанова");
        assertTrue(regular.valid());
        assertEquals("Мария Иванова", regular.normalizedFullName());

        FullNameValidator.FullNameValidationResult hyphenated = validator.validate("  олег   смирнов-петров  ");
        assertTrue(hyphenated.valid());
        assertEquals("Олег Смирнов-Петров", hyphenated.normalizedFullName());
    }
}
