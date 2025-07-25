package com.project.tracking_system.configuration;

import com.project.tracking_system.utils.ResponseBuilder;
import jakarta.persistence.EntityNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.http.ResponseEntity;

import java.util.Map;

/**
 * Глобальный обработчик REST-исключений.
 * Переводит стандартные и кастомные исключения в HTTP-ответы.
 */
@Slf4j
@RestControllerAdvice
public class RestExceptionHandler {

    /**
     * Обработка ошибок доступа.
     *
     * @param ex исключение доступа
     * @return ответ 403 с сообщением об ошибке
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, String>> handleAccessDenied(AccessDeniedException ex) {
        log.warn("Ошибка доступа: {}", ex.getMessage());
        return ResponseBuilder.error(HttpStatus.FORBIDDEN, ex.getMessage());
    }

    /**
     * Обработка ситуаций, когда сущность не найдена.
     *
     * @param ex исключение отсутствия сущности
     * @return ответ 404 с сообщением об ошибке
     */
    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleNotFound(EntityNotFoundException ex) {
        log.warn("Сущность не найдена: {}", ex.getMessage());
        return ResponseBuilder.error(HttpStatus.NOT_FOUND, ex.getMessage());
    }
}
