package com.project.tracking_system.dto;

import java.time.ZonedDateTime;

/**
 * @author Dmitriy Anisimov
 * @date 09.04.2025
 */
public record DeliveryDates(
        ZonedDateTime sendDate,
        ZonedDateTime receivedDate,
        ZonedDateTime returnedDate,
        ZonedDateTime arrivedDate
) {
    public DeliveryDates(ZonedDateTime send, ZonedDateTime recv, ZonedDateTime ret) {
        this(send, recv, ret, null);
    }
}
