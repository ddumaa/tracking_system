package com.project.tracking_system.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

/**
 * @author Dmitriy Anisimov
 * @date 21.03.2025
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class PostalServiceStatsDTO {

    private String postalService;
    private int sent;
    private int delivered;
    private int returned;
    private double sumDeliveryDays;
    private double sumPickupTimeDays;

    public double getAvgDeliveryDays() {
        return delivered > 0 ? sumDeliveryDays / delivered : 0.0;
    }

    public double getAvgPickupTimeDays() {
        int pickedUp = delivered + returned;
        return pickedUp > 0 ? sumPickupTimeDays / pickedUp : 0.0;
    }

}