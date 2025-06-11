package com.project.tracking_system.exception;

/**
 * Исключение, выбрасываемое когда попытка получить текущего
 * пользователя завершается неудачей из-за отсутствия аутентификации.
 */
public class UserNotAuthenticatedException extends RuntimeException {

    /**
     * Создаёт исключение с описанием причины.
     *
     * @param message сообщение об ошибке
     */
    public UserNotAuthenticatedException(String message) {
        super(message);
    }
}
