package com.project.tracking_system.util;

import lombok.experimental.UtilityClass;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * Утилита для удобного создания {@link ResponseEntity}.
 */
@UtilityClass
public class ResponseBuilder {

    /**
     * Возвращает успешный ответ с телом.
     *
     * @param body тело ответа
     * @param <T>  тип тела
     * @return ResponseEntity с кодом 200
     */
    public static <T> ResponseEntity<T> ok(T body) {
        return ResponseEntity.ok(body);
    }

    /**
     * Возвращает успешный ответ без тела.
     *
     * @return ResponseEntity с кодом 200 без содержимого
     */
    public static ResponseEntity<Void> ok() {
        return ResponseEntity.ok().build();
    }

    /**
     * Возвращает ответ с ошибкой без сообщения.
     *
     * @param status HTTP-статус ошибки
     * @return ResponseEntity с указанным статусом
     */
    public static ResponseEntity<Void> error(HttpStatus status) {
        return ResponseEntity.status(status).build();
    }

    /**
     * Возвращает ответ с ошибкой и сообщением.
     *
     * @param status  HTTP-статус ошибки
     * @param message текст сообщения
     * @return ResponseEntity с указанным статусом и сообщением
     */
    public static ResponseEntity<String> error(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(message);
    }
}
