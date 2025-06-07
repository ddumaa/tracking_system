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
 * Weekly aggregated statistics for a store.
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "tb_store_statistics_weekly",
       uniqueConstraints = @UniqueConstraint(columnNames = {"store_id", "period_year", "period_number"}))
public class StoreWeeklyStatistics {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "store_id", nullable = false)
    private Store store;

    @Column(name = "period_year", nullable = false)
    private int periodYear;

    @Column(name = "period_number", nullable = false)
    private int periodNumber;

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

    @Column(name = "updated_at")
    private ZonedDateTime updatedAt;

    /**
     * Average days between sending and delivery.
     */
    @Transient
    public BigDecimal getAverageDeliveryDays() {
        return delivered > 0
                ? sumDeliveryDays.divide(BigDecimal.valueOf(delivered), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
    }

    /**
     * Average days until parcel pickup (delivery or return).
     */
    @Transient
    public BigDecimal getAveragePickupDays() {
        int totalPickedUp = delivered + returned;
        return totalPickedUp > 0
                ? sumPickupDays.divide(BigDecimal.valueOf(totalPickedUp), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
    }
}
