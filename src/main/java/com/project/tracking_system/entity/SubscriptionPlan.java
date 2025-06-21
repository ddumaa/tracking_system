package com.project.tracking_system.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import java.util.Objects;
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
@Getter
@Setter
public class SubscriptionPlan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String code;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private BigDecimal price = BigDecimal.ZERO;

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
     * Позиция плана в отсортированном списке.
     */
    @Column(nullable = false)
    private int position;

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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SubscriptionPlan that = (SubscriptionPlan) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

}
