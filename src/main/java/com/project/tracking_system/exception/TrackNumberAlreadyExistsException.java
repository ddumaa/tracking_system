package com.project.tracking_system.exception;

/**
 * Исключение, указывающее на попытку присвоить трек-номер,
 * который уже существует у пользователя.
 */
public class TrackNumberAlreadyExistsException extends RuntimeException {

    /**
     * Создаёт исключение с указанным сообщением.
     *
     * @param message описание ошибки
     */
    public TrackNumberAlreadyExistsException(String message) {
        super(message);
    }
}
