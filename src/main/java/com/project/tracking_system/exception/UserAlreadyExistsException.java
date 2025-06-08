package com.project.tracking_system.exception;

/**
 * Исключение, возникающее при попытке зарегистрировать пользователя,
 * который уже существует в системе.
 */
public class UserAlreadyExistsException extends RuntimeException {

    /**
     * Создаёт исключение с указанным сообщением.
     *
     * @param message описание ошибки
     */
    public UserAlreadyExistsException(String message) {
        super(message);
    }
}
