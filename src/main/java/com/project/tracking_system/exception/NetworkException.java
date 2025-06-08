package com.project.tracking_system.exception;

/**
 * Исключение, сигнализирующее о проблемах сети при обращении к внешнему
 * сервису. Обычно возникает при ошибках REST-запросов или недоступности
 * удалённых серверов.
 */
public class NetworkException extends RuntimeException {

    /**
     * Создаёт исключение с описанием сетевой ошибки.
     *
     * @param message сообщение об ошибке
     */
    public NetworkException(String message) {
        super(message);
    }

    /**
     * Создаёт исключение с сообщением и причиной сетевой ошибки.
     *
     * @param message сообщение об ошибке
     * @param cause   исходная причина
     */
    public NetworkException(String message, Throwable cause) {
        super(message, cause);
    }
}
