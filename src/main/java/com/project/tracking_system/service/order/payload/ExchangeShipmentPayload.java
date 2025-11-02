package com.project.tracking_system.service.order.payload;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Полезная нагрузка для команд, фиксирующих обменную отправку.
 *
 * @param exchangeTrack нормализованный трек обменной посылки
 * @param stageMoment   момент фиксации стадии в UTC
 */
public record ExchangeShipmentPayload(String exchangeTrack,
                                      ZonedDateTime stageMoment) implements ReturnRequestCommandPayload {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    @Override
    public JsonNode toNormalizedTree(ObjectMapper objectMapper) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("exchangeTrack", exchangeTrack);
        if (stageMoment != null) {
            node.put("stageMoment", stageMoment.withZoneSameInstant(ZoneOffset.UTC).format(FORMATTER));
        }
        return node;
    }
}
