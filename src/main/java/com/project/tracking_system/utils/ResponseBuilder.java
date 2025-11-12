package com.project.tracking_system.utils;

import com.project.tracking_system.dto.ErrorResponseDto;
import com.project.tracking_system.dto.ReturnApiErrorCode;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * Утилита для упрощённого создания {@link ResponseEntity}.
 * <p>
 * Позволяет быстро формировать успешные и ошибочные ответы в контроллерах,
 * избегая дублирования кода.
 * </p>
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
     * Создаёт ответ с заданным статусом и деталями ошибки API.
     *
     * @param status статус HTTP
     * @param code   код ошибки из контракта
     * @param msg    сообщение об ошибке
     * @return объект {@link ResponseEntity} с унифицированным DTO ошибки
     */
    public static ResponseEntity<ErrorResponseDto> error(HttpStatus status,
                                                         ReturnApiErrorCode code,
                                                         String msg) {
        return ResponseEntity.status(status)
                .body(new ErrorResponseDto(code, msg));
    }

    /**
     * Создаёт ответ с ошибкой, автоматически подбирая код по статусу.
     *
     * @param status статус HTTP
     * @param msg    сообщение об ошибке
     * @return объект {@link ResponseEntity} с унифицированным DTO ошибки
     */
    public static ResponseEntity<ErrorResponseDto> error(HttpStatus status, String msg) {
        return error(status, resolveDefaultCode(status), msg);
    }

    /**
     * Подбирает значение кода ошибки в зависимости от статуса ответа.
     *
     * @param status статус HTTP
     * @return код ошибки, подходящий для стандартных сценариев
     */
    private static ReturnApiErrorCode resolveDefaultCode(HttpStatus status) {
        if (status == null) {
            return ReturnApiErrorCode.ACTION_NOT_ALLOWED;
        }
        return switch (status) {
            case NOT_FOUND -> ReturnApiErrorCode.ENTITY_NOT_FOUND;
            case FORBIDDEN -> ReturnApiErrorCode.FORBIDDEN;
            case UNPROCESSABLE_ENTITY, BAD_REQUEST -> ReturnApiErrorCode.VALIDATION_FAILED;
            case CONFLICT -> ReturnApiErrorCode.ACTION_NOT_ALLOWED;
            default -> ReturnApiErrorCode.ACTION_NOT_ALLOWED;
        };
    }
}
