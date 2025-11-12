package com.project.tracking_system.exception;

/**
 * Исключение, сигнализирующее, что запрошенное действие недоступно.
 * <p>
 * Используется в ситуациях, когда бизнес-инварианты не позволяют
 * продолжить операцию (например, статус заявки не соответствует требуемому).
 * </p>
 */
public class ActionNotAllowedException extends RuntimeException {

    /**
     * Создаёт исключение с описанием причины запрета.
     *
     * @param message текстовое описание проблемы
     */
    public ActionNotAllowedException(String message) {
        super(message);
    }

    /**
     * Создаёт исключение с указанием исходной причины.
     *
     * @param message текстовое описание проблемы
     * @param cause   исходное исключение
     */
    public ActionNotAllowedException(String message, Throwable cause) {
        super(message, cause);
    }
}
