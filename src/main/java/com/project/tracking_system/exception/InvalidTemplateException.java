package com.project.tracking_system.exception;

/**
 * Исключение, возникающее при некорректном пользовательском шаблоне Telegram.
 */
public class InvalidTemplateException extends RuntimeException {

    /**
     * Создаёт исключение с описанием ошибки.
     *
     * @param message подробности некорректности шаблона
     */
    public InvalidTemplateException(String message) {
        super(message);
    }
}
