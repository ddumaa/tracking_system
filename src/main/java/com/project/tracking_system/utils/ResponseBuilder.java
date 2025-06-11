package com.project.tracking_system.utils;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

/**
 * Утилита для упрощенного создания {@link ResponseEntity}.
 */
public final class ResponseBuilder {

    private ResponseBuilder() {
    }

    /**
     * Создает успешный ответ со статусом 200.
     *
     * @param data тело ответа
     * @param <T>  тип возвращаемых данных
     * @return объект {@link ResponseEntity} с указанными данными
     */
    public static <T> ResponseEntity<T> ok(T data) {
        return ResponseEntity.ok(data);
    }

    /**
     * Создает ответ с заданным статусом и сообщением об ошибке.
     *
     * @param status статус HTTP
     * @param msg    сообщение об ошибке
     * @return объект {@link ResponseEntity} с сообщением об ошибке
     */
    public static ResponseEntity<Map<String, String>> error(HttpStatus status, String msg) {
        return ResponseEntity.status(status).body(Map.of("error", msg));
    }
}
