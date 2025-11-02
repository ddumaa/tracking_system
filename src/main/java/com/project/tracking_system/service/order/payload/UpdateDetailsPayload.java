package com.project.tracking_system.service.order.payload;

import java.security.MessageDigest;

/**
 * Полезная нагрузка для команды обновления данных заявки.
 *
 * @param reverseTrack номер обратного трека в нормализованном виде
 * @param comment      комментарий менеджера
 */
public record UpdateDetailsPayload(String reverseTrack,
                                   String comment) implements ReturnRequestCommandPayload {

    @Override
    public void contributeTo(MessageDigest digest) {
        digest.update(nullSafeBytes(reverseTrack));
        digest.update(nullSafeBytes(comment));
    }
}
