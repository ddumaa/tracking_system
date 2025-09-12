package com.project.tracking_system.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Ежедневная статистика по почтовой службе.
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "tb_postal_service_statistics_daily")
public class PostalServiceDailyStatistics {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "store_id", nullable = false)
    private Store store;

    @Enumerated(EnumType.STRING)
    @Column(name = "postal_service_type", nullable = false)
    private PostalServiceType postalServiceType;

    @Column(name = "date", nullable = false)
    private LocalDate date;

    @Column(name = "sent", nullable = false)
    private int sent;

    @Column(name = "delivered", nullable = false)
    private int delivered;

    @Column(name = "returned", nullable = false)
    private int returned;

    @Column(name = "sum_delivery_days", nullable = false)
    private BigDecimal sumDeliveryDays = BigDecimal.ZERO;

    @Column(name = "sum_pickup_days", nullable = false)
    private BigDecimal sumPickupDays = BigDecimal.ZERO;

    /**
     * Момент последнего обновления статистики (UTC).
     */
    @Column(name = "updated_at")
    private Instant updatedAt;

    /**
     * Среднее количество дней от отправки до доставки.
     */
    @Transient
    public BigDecimal getAverageDeliveryDays() {
        return delivered > 0
                ? sumDeliveryDays.divide(BigDecimal.valueOf(delivered), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
    }

    /**
     * Среднее количество дней нахождения посылки на пункте выдачи (только доставленные).
     */
    @Transient
    public BigDecimal getAveragePickupDays() {
        return delivered > 0
                ? sumPickupDays.divide(BigDecimal.valueOf(delivered), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
    }
}
