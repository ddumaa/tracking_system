package com.project.tracking_system.service.order.payload;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Полезная нагрузка для команды обновления данных заявки.
 *
 * @param reverseTrack номер обратного трека в нормализованном виде
 * @param comment      комментарий менеджера
 */
public record UpdateDetailsPayload(String reverseTrack,
                                   String comment) implements ReturnRequestCommandPayload {

    @Override
    public JsonNode toNormalizedTree(ObjectMapper objectMapper) {
        ObjectNode node = objectMapper.createObjectNode();
        if (reverseTrack != null) {
            node.put("reverseTrack", reverseTrack);
        }
        if (comment != null) {
            node.put("comment", comment);
        }
        return node;
    }
}
