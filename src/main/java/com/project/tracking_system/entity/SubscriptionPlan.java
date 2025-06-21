package com.project.tracking_system.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @author Dmitriy Anisimov
 * @date 11.02.2025
 */
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "subscription_plans")
@Data
public class SubscriptionPlan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, unique = true)
    private SubscriptionCode code;

    @Column(nullable = false)
    private Integer maxTracksPerFile;

    @Column(nullable = false)
    private Integer maxSavedTracks;

    @Column(nullable = false)
    private Integer maxTrackUpdates;

    @Column(nullable = false)
    private boolean allowBulkUpdate;

    @Column(nullable = false)
    private Integer maxStores;

    @Column(name = "allow_telegram_notifications", nullable = false)
    private Boolean allowTelegramNotifications = false;

    @Column(name = "monthly_price", nullable = false)
    private java.math.BigDecimal monthlyPrice = java.math.BigDecimal.ZERO;

    @Column(name = "annual_price", nullable = false)
    private java.math.BigDecimal annualPrice = java.math.BigDecimal.ZERO;
}