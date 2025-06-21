package com.project.tracking_system.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.project.tracking_system.model.subscription.FeatureKey;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

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

    @OneToMany(mappedBy = "subscriptionPlan", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<SubscriptionFeature> features = new ArrayList<>();

    @Column(name = "monthly_price", nullable = false)
    private java.math.BigDecimal monthlyPrice = java.math.BigDecimal.ZERO;

    @Column(name = "annual_price", nullable = false)
    private java.math.BigDecimal annualPrice = java.math.BigDecimal.ZERO;

    /**
     * Проверяет, доступна ли указанная возможность в тарифе.
     *
     * @param key ключ функции
     * @return {@code true}, если возможность включена
     */
    public boolean isFeatureEnabled(FeatureKey key) {
        if (key == null || features == null) {
            return false;
        }

        // ищем соответствующую настройку в списке функций
        return features.stream()
                .anyMatch(f -> key.equals(f.getFeatureKey()) && f.isEnabled());
    }

}
