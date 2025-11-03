package com.project.tracking_system.service.order.payload;

import com.fasterxml.jackson.databind.JsonNode;
import com.project.tracking_system.controller.ReturnRequestCommandType;
import org.springframework.stereotype.Component;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.util.EnumMap;
import java.util.Map;

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

    private final Map<ReturnRequestCommandType, PayloadParser> parsers;

    /**
     * Регистрирует обработчики полезной нагрузки для всех поддерживаемых команд.
     */
    public ReturnRequestCommandPayloadFactory() {
        EnumMap<ReturnRequestCommandType, PayloadParser> registry = new EnumMap<>(ReturnRequestCommandType.class);
        registry.put(ReturnRequestCommandType.SET_MODE_EXCHANGE, emptyParser(ReturnRequestCommandType.SET_MODE_EXCHANGE));
        registry.put(ReturnRequestCommandType.SET_MODE_RETURN, emptyParser(ReturnRequestCommandType.SET_MODE_RETURN));
        registry.put(ReturnRequestCommandType.CANCEL_EXCHANGE, emptyParser(ReturnRequestCommandType.CANCEL_EXCHANGE));
        registry.put(ReturnRequestCommandType.CLOSE_REQUEST, emptyParser(ReturnRequestCommandType.CLOSE_REQUEST));
        registry.put(ReturnRequestCommandType.REGISTER_EXCHANGE_PARCEL,
                exchangeParser(ReturnRequestCommandType.REGISTER_EXCHANGE_PARCEL));
        registry.put(ReturnRequestCommandType.MARK_EXCHANGE_SENT,
                exchangeParser(ReturnRequestCommandType.MARK_EXCHANGE_SENT));
        registry.put(ReturnRequestCommandType.MARK_EXCHANGE_DELIVERED,
                stageParser(ReturnRequestCommandType.MARK_EXCHANGE_DELIVERED));
        registry.put(ReturnRequestCommandType.MARK_OUTBOUND_SENT,
                stageParser(ReturnRequestCommandType.MARK_OUTBOUND_SENT));
        registry.put(ReturnRequestCommandType.MARK_INBOUND_ARRIVED,
                stageParser(ReturnRequestCommandType.MARK_INBOUND_ARRIVED));
        registry.put(ReturnRequestCommandType.MARK_INBOUND_PICKED_UP,
                stageParser(ReturnRequestCommandType.MARK_INBOUND_PICKED_UP));
        registry.put(ReturnRequestCommandType.UPDATE_REVERSE_TRACK, this::parseUpdateDetails);
        this.parsers = Map.copyOf(registry);
    }

    /**
     * Валидирует и создаёт полезную нагрузку для переданной команды.
     *
     * @param type        тип команды
     * @param payloadNode узел JSON с параметрами
     * @return нормализованная полезная нагрузка
     */
    public ReturnRequestCommandPayload create(ReturnRequestCommandType type, JsonNode payloadNode) {
        PayloadParser parser = parsers.get(type);
        if (parser == null) {
            throw new IllegalArgumentException("Команда " + type.name() + " не поддерживается системой");
        }
        return parser.parse(payloadNode);
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
            throw new IllegalArgumentException("Команда UPDATE_REVERSE_TRACK требует объект payload с параметрами");
        }
        if (!payloadNode.isObject()) {
            throw new IllegalArgumentException("Полезная нагрузка UPDATE_REVERSE_TRACK должна быть объектом JSON");
        }

        boolean hasReverseTrack = payloadNode.has("reverseTrack");
        boolean hasComment = payloadNode.has("comment");
        if (!hasReverseTrack && !hasComment) {
            throw new IllegalArgumentException("Не переданы поля reverseTrack или comment для UPDATE_REVERSE_TRACK");
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

    /**
     * Возвращает обработчик для команд без параметров.
     */
    private PayloadParser emptyParser(ReturnRequestCommandType type) {
        return payloadNode -> ensureEmptyPayload(type, payloadNode);
    }

    /**
     * Возвращает обработчик стадийных команд.
     */
    private PayloadParser stageParser(ReturnRequestCommandType type) {
        return payloadNode -> parseStageMarkPayload(type, payloadNode);
    }

    /**
     * Возвращает обработчик команд, работающих с обменной отправкой.
     */
    private PayloadParser exchangeParser(ReturnRequestCommandType type) {
        return payloadNode -> parseExchangeShipmentPayload(type, payloadNode);
    }

    /**
     * Функциональный интерфейс обработчика конкретного типа payload.
     */
    @FunctionalInterface
    private interface PayloadParser {
        ReturnRequestCommandPayload parse(JsonNode payloadNode);
    }
}
