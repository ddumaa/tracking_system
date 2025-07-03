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

    /**
     * Среднее время доставки в днях.
     *
     * @return среднее количество дней доставки или 0, если доставленных нет
     */
    public double getAvgDeliveryDays() {
        return delivered > 0 ? sumDeliveryDays / delivered : 0.0;
    }

    /**
     * Среднее время нахождения посылки на пункте выдачи в днях.
     * Рассчитывается только по доставленным отправлениям.
     *
     * @return среднее время ожидания клиента или 0, если доставленных нет
     */
    public double getAvgPickupTimeDays() {
        return delivered > 0 ? sumPickupTimeDays / delivered : 0.0;
    }

}