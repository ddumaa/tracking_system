package com.project.tracking_system.service.order.payload;

import com.fasterxml.jackson.databind.JsonNode;
import com.project.tracking_system.controller.ReturnRequestCommandType;
import org.springframework.stereotype.Component;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;

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
            case SET_MODE_EXCHANGE, SET_MODE_RETURN -> ensureEmptyPayload(type, payloadNode);
            case CLOSE_REQUEST -> ensureEmptyPayload(type, payloadNode);
            case MARK_OUTBOUND_SENT, MARK_INBOUND_ARRIVED, MARK_INBOUND_PICKED_UP ->
                    parseStageMarkPayload(type, payloadNode);
            case UPDATE_REVERSE_TRACK -> parseUpdateReverseTrack(payloadNode);
            case REGISTER_EXCHANGE_PARCEL, MARK_EXCHANGE_SENT, MARK_EXCHANGE_DELIVERED ->
                    parseExchangeShipmentPayload(type, payloadNode);
            default -> throw new IllegalArgumentException("Команда " + type.name() + " не поддерживается системой");
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
     * Разбирает полезную нагрузку команды обновления обратного трека.
     */
    private ReturnRequestCommandPayload parseUpdateReverseTrack(JsonNode payloadNode) {
        if (payloadNode == null || payloadNode.isNull()) {
            throw new IllegalArgumentException("Команда UPDATE_REVERSE_TRACK требует объект payload с параметрами");
        }
        if (!payloadNode.isObject()) {
            throw new IllegalArgumentException("Полезная нагрузка UPDATE_REVERSE_TRACK должна быть объектом JSON");
        }

        String reverseTrack = requireReverseTrack(payloadNode.get("reverseTrack"));
        String comment = normalizeComment(payloadNode.get("comment"));

        return new UpdateReverseTrackPayload(reverseTrack, comment);
    }

    /**
     * Разбирает полезную нагрузку команд, фиксирующих переход по стадии.
     */
    private ReturnRequestCommandPayload parseStageMarkPayload(ReturnRequestCommandType type, JsonNode payloadNode) {
        if (payloadNode == null || payloadNode.isNull()) {
            return new StageMarkPayload(null);
        }
        if (!payloadNode.isObject()) {
            throw new IllegalArgumentException("Полезная нагрузка " + type.name() + " должна быть объектом JSON");
        }
        ZonedDateTime stageMoment = parseStageMoment(payloadNode.get("stageMoment"), type);
        return new StageMarkPayload(stageMoment);
    }

    /**
     * Разбирает payload команд регистрации и отправки обменной посылки.
     */
    private ReturnRequestCommandPayload parseExchangeShipmentPayload(ReturnRequestCommandType type, JsonNode payloadNode) {
        if (payloadNode == null || payloadNode.isNull()) {
            throw new IllegalArgumentException("Команда " + type.name() + " требует объект payload с параметрами");
        }
        if (!payloadNode.isObject()) {
            throw new IllegalArgumentException("Полезная нагрузка " + type.name() + " должна быть объектом JSON");
        }
        String exchangeTrack = requireExchangeTrack(payloadNode.get("exchangeTrack"), type);
        ZonedDateTime stageMoment = parseStageMoment(payloadNode.get("stageMoment"), type);
        return new ExchangeShipmentPayload(exchangeTrack, stageMoment);
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
            throw new IllegalArgumentException("Поле " + fieldName + " не должно превышать 64 символа");
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

    /**
     * Валидирует наличие и формат трека обмена.
     */
    private String requireExchangeTrack(JsonNode node, ReturnRequestCommandType type) {
        String track = normalizeTrack(node, "exchangeTrack");
        if (track == null) {
            throw new IllegalArgumentException("Поле exchangeTrack обязательно для команды " + type.name());
        }
        return track;
    }

    /**
     * Проверяет наличие обязательного обратного трека и приводит его к нормализованному виду.
     */
    private String requireReverseTrack(JsonNode node) {
        String track = normalizeTrack(node, "reverseTrack");
        if (track == null) {
            throw new IllegalArgumentException("Поле reverseTrack обязательно для команды UPDATE_REVERSE_TRACK");
        }
        return track;
    }

    /**
     * Разбирает момент наступления стадии, приводя его к UTC.
     */
    private ZonedDateTime parseStageMoment(JsonNode node, ReturnRequestCommandType type) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (!node.isTextual()) {
            throw new IllegalArgumentException("Поле stageMoment команды " + type.name() + " должно быть строкой");
        }
        String text = node.asText().trim();
        if (text.isEmpty()) {
            return null;
        }
        try {
            return ZonedDateTime.parse(text).withZoneSameInstant(ZoneOffset.UTC);
        } catch (DateTimeParseException ex) {
            throw new IllegalArgumentException("Поле stageMoment команды " + type.name() + " имеет неверный формат", ex);
        }
    }

}
