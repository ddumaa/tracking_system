package com.project.tracking_system.service.order.payload;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Полезная нагрузка с информацией о моменте наступления стадии заявки.
 *
 * @param stageMoment момент фиксации стадии в UTC
 */
public record StageMarkPayload(ZonedDateTime stageMoment) implements ReturnRequestCommandPayload {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    @Override
    public JsonNode toNormalizedTree(ObjectMapper objectMapper) {
        ObjectNode node = objectMapper.createObjectNode();
        if (stageMoment != null) {
            node.put("stageMoment", stageMoment.withZoneSameInstant(ZoneOffset.UTC).format(FORMATTER));
        }
        return node;
    }
}
