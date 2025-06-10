package com.project.tracking_system.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.ZonedDateTime;

/**
 * @author Dmitriy Anisimov
 * @date 11.03.2025
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "tb_store_statistics")
public class StoreStatistics {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "store_id", nullable = false)
    private Store store;

    @Column(name = "total_sent", nullable = false)
    private int totalSent;

    @Column(name = "total_delivered", nullable = false)
    private int totalDelivered;

    @Column(name = "total_returned", nullable = false)
    private int totalReturned;

    @Column(name = "sum_delivery_days", nullable = false)
    private BigDecimal sumDeliveryDays = BigDecimal.ZERO;

    @Column(name = "sum_pickup_days", nullable = false)
    private BigDecimal sumPickupDays = BigDecimal.ZERO;

    @Column(name = "updated_at")
    private ZonedDateTime updatedAt;

    /**
     * Возвращает среднее время доставки заказа в днях.
     * <p>
     * Поле {@code sumDeliveryDays} содержит накопленную длительность доставки в днях.
     * Деление на количество доставленных отправлений выполняется лениво в момент вызова метода.
     * </p>
     *
     * @return среднее время доставки в днях, округлённое до двух знаков
     */
    @Transient
    public BigDecimal getAverageDeliveryDays() {
        return totalDelivered > 0
                ? sumDeliveryDays.divide(BigDecimal.valueOf(totalDelivered), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
    }

    /**
     * Возвращает среднее время получения посылки клиентом в днях.
     * <p>
     * Поле {@code sumPickupDays} накапливает длительности ожидания получения в днях.
     * Среднее вычисляется лениво на основе количества доставленных отправлений.
     * </p>
     *
     * @return среднее время получения в днях, округлённое до двух знаков
     */
    @Transient
    public BigDecimal getAveragePickupDays() {
        return totalDelivered > 0
                ? sumPickupDays.divide(BigDecimal.valueOf(totalDelivered), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
    }

    @Transient
    public BigDecimal getDeliverySuccessRate() {
        int total = totalDelivered + totalReturned;
        return total > 0
                ? BigDecimal.valueOf(totalDelivered * 100.0 / total).setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
    }

    @Transient
    public BigDecimal getReturnRate() {
        int total = totalDelivered + totalReturned;
        return total > 0
                ? BigDecimal.valueOf(totalReturned * 100.0 / total).setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
    }

}