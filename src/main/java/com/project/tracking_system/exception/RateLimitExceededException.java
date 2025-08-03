package com.project.tracking_system.exception;

/**
 * Исключение выбрасывается при превышении лимита запросов.
 */
public class RateLimitExceededException extends RuntimeException {
    public RateLimitExceededException(String message) {
        super(message);
    }
}
