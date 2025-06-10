package com.project.tracking_system.exception;

/**
 * Исключение, которое выбрасывается при использовании некорректного или
 * истёкшего токена.
 * <p>
 * Применяется для уведомления об ошибках аутентификации или подтверждения
 * через одноразовые токены.
 */
public class InvalidTokenException extends RuntimeException {

    /**
     * Создаёт исключение с указанным сообщением.
     *
     * @param message описание проблемы
     */
    public InvalidTokenException(String message) {
        super(message);
    }

    /**
     * Создаёт исключение с сообщением и причиной возникновения.
     *
     * @param message описание проблемы
     * @param cause   исходная причина ошибки
     */
    public InvalidTokenException(String message, Throwable cause) {
        super(message, cause);
    }
}

