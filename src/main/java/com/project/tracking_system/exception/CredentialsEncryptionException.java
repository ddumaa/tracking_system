package com.project.tracking_system.exception;

/**
 * Исключение, возникающее при ошибке шифрования или дешифрования данных.
 */
public class CredentialsEncryptionException extends RuntimeException {

    /**
     * Создаёт исключение с указанным сообщением и причиной.
     *
     * @param message описание ошибки
     * @param cause   первопричина исключения
     */
    public CredentialsEncryptionException(String message, Throwable cause) {
        super(message, cause);
    }
}
