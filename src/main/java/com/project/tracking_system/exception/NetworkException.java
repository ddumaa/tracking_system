package com.project.tracking_system.exception;

/**
 * @author Dmitriy Anisimov
 * @date 09.02.2025
 */
public class NetworkException extends RuntimeException {
    public NetworkException(String message) {
        super(message);
    }

    public NetworkException(String message, Throwable cause) {
        super(message, cause);
    }
}