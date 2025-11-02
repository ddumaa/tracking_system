package com.project.tracking_system.service.order.payload;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Пустая полезная нагрузка, используемая для команд без параметров.
 */
enum EmptyPayload implements ReturnRequestCommandPayload {
    INSTANCE;

    @Override
    public JsonNode toNormalizedTree(ObjectMapper objectMapper) {
        return objectMapper.createObjectNode();
    }
}
