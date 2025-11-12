package com.project.tracking_system.exception;

/**
 * Исключение для конфликтов идемпотентности.
 * <p>
 * Выбрасывается при повторных запросах с одинаковым ключом, когда данные
 * отличаются, либо операция уже выполняется другим потоком.
 * </p>
 */
public class IdempotencyConflictException extends RuntimeException {

    /**
     * Создаёт исключение с описанием конфликта.
     *
     * @param message текстовое описание проблемы
     */
    public IdempotencyConflictException(String message) {
        super(message);
    }

    /**
     * Создаёт исключение с указанием исходной причины.
     *
     * @param message текстовое описание проблемы
     * @param cause   исходное исключение
     */
    public IdempotencyConflictException(String message, Throwable cause) {
        super(message, cause);
    }
}
