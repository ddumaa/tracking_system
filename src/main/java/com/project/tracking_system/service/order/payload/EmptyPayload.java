package com.project.tracking_system.service.order.payload;

import java.security.MessageDigest;

/**
 * Пустая полезная нагрузка, используемая для команд без параметров.
 */
enum EmptyPayload implements ReturnRequestCommandPayload {
    INSTANCE;

    @Override
    public void contributeTo(MessageDigest digest) {
        // Пустой payload не влияет на хеш команды.
    }
}
