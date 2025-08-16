package com.project.tracking_system.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Исключение, выбрасываемое при попытке магазина изменить подтверждённое имя.
 */
@ResponseStatus(HttpStatus.CONFLICT)
public class ConfirmedNameChangeException extends RuntimeException {

    public ConfirmedNameChangeException(String message) {
        super(message);
    }
}
