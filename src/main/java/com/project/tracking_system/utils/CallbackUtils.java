package com.project.tracking_system.utils;

import com.project.tracking_system.model.customer.CustomerNameDecision;

/**
 * Утилита формирования callback-строк для Telegram.
 */
public final class CallbackUtils {

    private CallbackUtils() {
    }

    /**
     * Сформировать callback для подтверждения ФИО покупателем.
     *
     * @param eventId  идентификатор события
     * @param decision действие покупателя
     * @return строка формата {@code NAME_VERIFY:{eventId}:{action}}
     */
    public static String buildNameVerify(Long eventId, CustomerNameDecision decision) {
        return String.format("NAME_VERIFY:%d:%s", eventId, decision);
    }
}
