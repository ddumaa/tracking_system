package com.project.tracking_system.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;

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

    @Column(nullable = false, unique = true)
    private String code;

    @Column(nullable = false)
    private String name;

    private String description;

    @Column(nullable = false)
    private BigDecimal price = BigDecimal.ZERO;

    @Column(name = "duration_days")
    private Integer durationDays;

    @Column(nullable = false)
    private Boolean active = true;


    @OneToOne(mappedBy = "subscriptionPlan", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private SubscriptionLimits limits;

    @Column(name = "monthly_price", nullable = false)
    private java.math.BigDecimal monthlyPrice = java.math.BigDecimal.ZERO;

    @Column(name = "annual_price", nullable = false)
    private java.math.BigDecimal annualPrice = java.math.BigDecimal.ZERO;

}
