package com.project.tracking_system.configuration;

import com.project.tracking_system.dto.ErrorResponseDto;
import com.project.tracking_system.dto.ReturnApiErrorCode;
import com.project.tracking_system.exception.ActionNotAllowedException;
import com.project.tracking_system.exception.IdempotencyConflictException;
import com.project.tracking_system.exception.ValidationException;
import com.project.tracking_system.utils.ResponseBuilder;
import jakarta.persistence.EntityNotFoundException;
import jakarta.persistence.OptimisticLockException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.support.DefaultMessageSourceResolvable;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Objects;
import java.util.stream.Collectors;

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
    public ResponseEntity<ErrorResponseDto> handleAccessDenied(AccessDeniedException ex) {
        log.warn("Ошибка доступа: {}", ex.getMessage());
        return ResponseBuilder.error(HttpStatus.FORBIDDEN, ReturnApiErrorCode.FORBIDDEN, safeMessage(ex));
    }

    /**
     * Обработка ситуаций, когда сущность не найдена.
     *
     * @param ex исключение отсутствия сущности
     * @return ответ 404 с сообщением об ошибке
     */
    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<ErrorResponseDto> handleNotFound(EntityNotFoundException ex) {
        log.warn("Сущность не найдена: {}", ex.getMessage());
        return ResponseBuilder.error(HttpStatus.NOT_FOUND, ReturnApiErrorCode.ENTITY_NOT_FOUND, safeMessage(ex));
    }

    /**
     * Обрабатывает ошибки прикладной валидации, выброшенные сервисным слоем.
     *
     * @param ex исключение валидации
     * @return ответ 422 с кодом VALIDATION_FAILED
     */
    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<ErrorResponseDto> handleValidation(ValidationException ex) {
        log.debug("Ошибка валидации: {}", ex.getMessage());
        return ResponseBuilder.error(HttpStatus.UNPROCESSABLE_ENTITY, ReturnApiErrorCode.VALIDATION_FAILED, safeMessage(ex));
    }

    /**
     * Обрабатывает ошибки валидации параметров контроллера.
     *
     * @param ex исключение привязки аргументов
     * @return ответ 422 с кодом VALIDATION_FAILED
     */
    @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class, ConstraintViolationException.class})
    public ResponseEntity<ErrorResponseDto> handleMethodArgumentNotValid(Exception ex) {
        String message;
        if (ex instanceof MethodArgumentNotValidException manv) {
            message = extractBindingMessage(manv);
        } else if (ex instanceof BindException bind) {
            message = extractBindingMessage(bind);
        } else if (ex instanceof ConstraintViolationException violation) {
            message = extractConstraintMessage(violation);
        } else {
            message = safeMessage(ex);
        }
        log.debug("Ошибка валидации запроса: {}", message);
        return ResponseBuilder.error(HttpStatus.UNPROCESSABLE_ENTITY, ReturnApiErrorCode.VALIDATION_FAILED, message);
    }

    /**
     * Обрабатывает ситуации, когда действие запрещено бизнес-логикой.
     *
     * @param ex исключение запрета действия
     * @return ответ 409 с кодом ACTION_NOT_ALLOWED
     */
    @ExceptionHandler(ActionNotAllowedException.class)
    public ResponseEntity<ErrorResponseDto> handleActionNotAllowed(ActionNotAllowedException ex) {
        log.debug("Действие недоступно: {}", ex.getMessage());
        return ResponseBuilder.error(HttpStatus.CONFLICT, ReturnApiErrorCode.ACTION_NOT_ALLOWED, safeMessage(ex));
    }

    /**
     * Обрабатывает конфликты идемпотентности.
     *
     * @param ex исключение идемпотентности
     * @return ответ 409 с кодом IDEMPOTENCY_CONFLICT
     */
    @ExceptionHandler(IdempotencyConflictException.class)
    public ResponseEntity<ErrorResponseDto> handleIdempotency(IdempotencyConflictException ex) {
        log.debug("Конфликт идемпотентности: {}", ex.getMessage());
        return ResponseBuilder.error(HttpStatus.CONFLICT, ReturnApiErrorCode.IDEMPOTENCY_CONFLICT, safeMessage(ex));
    }

    /**
     * Обрабатывает ошибки оптимистических блокировок JPA и Spring Data.
     *
     * @param ex исключение блокировки
     * @return ответ 409 с кодом OPTIMISTIC_LOCK
     */
    @ExceptionHandler({OptimisticLockException.class, OptimisticLockingFailureException.class})
    public ResponseEntity<ErrorResponseDto> handleOptimisticLock(Exception ex) {
        log.warn("Конфликт оптимистической блокировки: {}", ex.getMessage());
        return ResponseBuilder.error(HttpStatus.CONFLICT, ReturnApiErrorCode.OPTIMISTIC_LOCK, safeMessage(ex));
    }

    /**
     * Создаёт безопасное текстовое сообщение, защищаясь от {@code null}.
     *
     * @param ex исходное исключение
     * @return текст сообщения или дефолт, если сообщение отсутствует
     */
    private String safeMessage(Throwable ex) {
        String message = ex.getMessage();
        return message != null ? message : "Внутренняя ошибка";
    }

    /**
     * Собирает сообщение об ошибке из результатов привязки аргументов.
     *
     * @param ex исключение привязки
     * @return объединённое сообщение для клиента
     */
    private String extractBindingMessage(BindException ex) {
        return ex.getBindingResult().getAllErrors().stream()
                .map(DefaultMessageSourceResolvable::getDefaultMessage)
                .filter(Objects::nonNull)
                .collect(Collectors.joining(", "));
    }

    /**
     * Формирует сообщение из нарушений Bean Validation.
     *
     * @param ex исключение нарушений
     * @return объединённое сообщение для клиента
     */
    private String extractConstraintMessage(ConstraintViolationException ex) {
        return ex.getConstraintViolations().stream()
                .map(ConstraintViolation::getMessage)
                .filter(Objects::nonNull)
                .collect(Collectors.joining(", "));
    }
}
