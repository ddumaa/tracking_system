package com.project.tracking_system.exception;

/**
 * Исключение, возникающее при генерации JWT токена.
 */
public class JwtTokenGenerationException extends RuntimeException {

    /**
     * Создаёт исключение с указанным сообщением.
     *
     * @param message описание ошибки
     */
    public JwtTokenGenerationException(String message) {
        super(message);
    }
}
