package com.project.tracking_system.service.order.payload;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Маркерный интерфейс полезной нагрузки команды управления заявкой.
 * <p>
 * Каждая реализация возвращает нормализованный JSON, который используется для расчёта хеша
 * и обеспечения идемпотентности команд даже при вложенных структурах.
 * </p>
 */
public sealed interface ReturnRequestCommandPayload permits EmptyPayload, UpdateDetailsPayload,
        StageMarkPayload, ExchangeShipmentPayload {

    /**
     * Возвращает нормализованное дерево JSON, содержащее параметры команды.
     *
     * @param objectMapper mapper, используемый для создания узлов
     * @return объект JSON с параметрами команды
     */
    JsonNode toNormalizedTree(ObjectMapper objectMapper);

    /**
     * Возвращает сериализованный нормализованный JSON полезной нагрузки.
     *
     * @param objectMapper mapper, используемый для сериализации
     * @return массив байтов JSON-представления
     */
    default byte[] normalizedBytes(ObjectMapper objectMapper) {
        try {
            return objectMapper.writeValueAsBytes(toNormalizedTree(objectMapper));
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Не удалось сериализовать полезную нагрузку команды", ex);
        }
    }

    /**
     * Вносит нормализованное представление payload в digest для расчёта хеша.
     */
    default void updateDigest(MessageDigest digest, ObjectMapper objectMapper) {
        digest.update(normalizedBytes(objectMapper));
    }

    /**
     * Возвращает singleton пустой полезной нагрузки.
     *
     * @return экземпляр пустой полезной нагрузки
     */
    static ReturnRequestCommandPayload empty() {
        return EmptyPayload.INSTANCE;
    }

    /**
     * Преобразует строку в массив байтов без выброса NPE.
     */
    default byte[] nullSafeBytes(String value) {
        return value != null ? value.getBytes(StandardCharsets.UTF_8) : new byte[0];
    }
}
