package com.project.tracking_system.entity;

import com.project.tracking_system.model.subscription.FeatureKey;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Запись о доступной функции тарифного плана.
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "subscription_features", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"subscription_plan_id", "feature_key"})
})
public class SubscriptionFeature {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "feature_key", nullable = false)
    private FeatureKey featureKey;

    @Column(nullable = false)
    private boolean enabled = false;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subscription_plan_id", nullable = false)
    private SubscriptionPlan subscriptionPlan;
}
