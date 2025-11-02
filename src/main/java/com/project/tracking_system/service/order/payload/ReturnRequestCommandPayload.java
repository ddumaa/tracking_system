package com.project.tracking_system.service.order.payload;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Маркерный интерфейс полезной нагрузки команды управления заявкой.
 * <p>
 * Каждая реализация отвечает за сериализацию данных в хеш, чтобы обеспечить
 * идемпотентность выполнения команд с одинаковыми параметрами.
 * </p>
 */
public sealed interface ReturnRequestCommandPayload permits EmptyPayload, UpdateDetailsPayload {

    /**
     * Вносит данные полезной нагрузки в указанный MessageDigest.
     *
     * @param digest поток, в который добавляются байты параметров
     */
    void contributeTo(MessageDigest digest);

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
