package com.project.tracking_system.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @author Dmitriy Anisimov
 * @date 21.03.2025
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class PostalServiceStatsDTO {

    private String serviceName;
    private int sent;
    private int delivered;
    private int returned;
    private long avgDeliveryDays;
    private long avgPickupTimeDays;

}