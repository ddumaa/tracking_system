package com.project.tracking_system.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.ZonedDateTime;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "tb_postal_service_statistics")
public class PostalServiceStatistics {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "store_id", nullable = false)
    private Store store;

    @Enumerated(EnumType.STRING)
    @Column(name = "postal_service_type", nullable = false)
    private PostalServiceType postalServiceType;

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
     * Возвращает среднее время доставки через эту службу в днях.
     * <p>
     * Поле {@code sumDeliveryDays} содержит суммарную длительность доставки в днях.
     * Деление на количество доставленных отправлений производится лениво при вызове метода.
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
     * Возвращает среднее время ожидания получения отправлений через эту службу в днях.
     * <p>
     * Поле {@code sumPickupDays} накапливает длительности ожидания в днях.
     * Среднее вычисляется лениво исходя из количества доставленных посылок.
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
