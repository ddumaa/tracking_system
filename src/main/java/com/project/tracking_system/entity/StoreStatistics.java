package com.project.tracking_system.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
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

    @Column(name = "average_delivery_days")
    private Double averageDeliveryDays;

    @Column(name = "avg_pickup_days")
    private Double averagePickupDays;

    @Column(name = "delivery_success_rate", precision = 5, scale = 2)
    private BigDecimal deliverySuccessRate;

    @Column(name = "return_rate", precision = 5, scale = 2)
    private BigDecimal returnRate;

    @Column(name = "updated_at")
    private ZonedDateTime updatedAt;

}