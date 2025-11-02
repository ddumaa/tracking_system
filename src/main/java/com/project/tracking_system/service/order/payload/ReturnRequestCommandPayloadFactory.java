package com.project.tracking_system.service.order.payload;

import com.fasterxml.jackson.databind.JsonNode;
import com.project.tracking_system.controller.ReturnRequestCommandType;
import org.springframework.stereotype.Component;

/**
 * Фабрика валидации полезной нагрузки команд управления заявками.
 * <p>
 * Класс инкапсулирует правила для каждой команды, гарантируя понятные сообщения
 * об ошибках и единый формат данных для бизнес-слоя.
 * </p>
 */
@Component
public class ReturnRequestCommandPayloadFactory {

    private static final int MAX_TRACK_LENGTH = 64;
    private static final int MAX_COMMENT_LENGTH = 2000;

    /**
     * Валидирует и создаёт полезную нагрузку для переданной команды.
     *
     * @param type        тип команды
     * @param payloadNode узел JSON с параметрами
     * @return нормализованная полезная нагрузка
     */
    public ReturnRequestCommandPayload create(ReturnRequestCommandType type, JsonNode payloadNode) {
        return switch (type) {
            case UPDATE_DETAILS -> parseUpdateDetails(payloadNode);
            case START_EXCHANGE, CREATE_EXCHANGE_PARCEL, CLOSE, CONFIRM_RECEIPT,
                    REOPEN_RETURN, CANCEL_EXCHANGE -> ensureEmptyPayload(type, payloadNode);
        };
    }

    /**
     * Проверяет, что команда не содержит параметров.
     */
    private ReturnRequestCommandPayload ensureEmptyPayload(ReturnRequestCommandType type,
                                                           JsonNode payloadNode) {
        if (payloadNode == null || payloadNode.isNull()) {
            return ReturnRequestCommandPayload.empty();
        }
        if (payloadNode.isObject() && payloadNode.size() == 0) {
            return ReturnRequestCommandPayload.empty();
        }
        throw new IllegalArgumentException("Команда " + type.name() + " не принимает параметры");
    }

    /**
     * Разбирает полезную нагрузку команды обновления данных заявки.
     */
    private ReturnRequestCommandPayload parseUpdateDetails(JsonNode payloadNode) {
        if (payloadNode == null || payloadNode.isNull()) {
            throw new IllegalArgumentException("Команда UPDATE_DETAILS требует объект payload с параметрами");
        }
        if (!payloadNode.isObject()) {
            throw new IllegalArgumentException("Полезная нагрузка UPDATE_DETAILS должна быть объектом JSON");
        }

        boolean hasReverseTrack = payloadNode.has("reverseTrack");
        boolean hasComment = payloadNode.has("comment");
        if (!hasReverseTrack && !hasComment) {
            throw new IllegalArgumentException("Не переданы поля reverseTrack или comment для UPDATE_DETAILS");
        }

        String reverseTrack = null;
        if (hasReverseTrack) {
            reverseTrack = normalizeTrack(payloadNode.get("reverseTrack"), "reverseTrack");
        }

        String comment = null;
        if (hasComment) {
            comment = normalizeComment(payloadNode.get("comment"));
        }

        return new UpdateDetailsPayload(reverseTrack, comment);
    }

    /**
     * Валидирует и нормализует строку трек-номера.
     */
    private String normalizeTrack(JsonNode node, String fieldName) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (!node.isTextual()) {
            throw new IllegalArgumentException("Поле " + fieldName + " должно быть строкой");
        }
        String normalized = node.asText().trim();
        if (normalized.isEmpty()) {
            return null;
        }
        if (normalized.length() > MAX_TRACK_LENGTH) {
            throw new IllegalArgumentException("Трек обратной отправки не должен превышать 64 символа");
        }
        return normalized.toUpperCase();
    }

    /**
     * Валидирует и нормализует комментарий менеджера.
     */
    private String normalizeComment(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (!node.isTextual()) {
            throw new IllegalArgumentException("Поле comment должно быть строкой");
        }
        String normalized = node.asText().trim();
        if (normalized.isEmpty()) {
            return null;
        }
        if (normalized.length() > MAX_COMMENT_LENGTH) {
            throw new IllegalArgumentException("Комментарий не должен превышать 2000 символов");
        }
        return normalized;
    }
}
