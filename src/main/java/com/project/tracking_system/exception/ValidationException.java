package com.project.tracking_system.exception;

/**
 * Исключение прикладной валидации входящих данных.
 * <p>
 * Позволяет явно сигнализировать REST-слою о том, что запрос содержит
 * некорректные параметры и должен быть отклонён с кодом 422.
 * </p>
 */
public class ValidationException extends RuntimeException {

    /**
     * Создаёт исключение с сообщением об ошибке валидации.
     *
     * @param message описание нарушения правил валидации
     */
    public ValidationException(String message) {
        super(message);
    }
}
