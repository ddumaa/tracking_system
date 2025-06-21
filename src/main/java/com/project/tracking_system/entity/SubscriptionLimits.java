package com.project.tracking_system.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Лимиты тарифного плана.
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "subscription_limits")
public class SubscriptionLimits {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne
    @JoinColumn(name = "subscription_plan_id", nullable = false, unique = true)
    private SubscriptionPlan subscriptionPlan;

    private Integer maxTracksPerFile;

    private Integer maxSavedTracks;

    private Integer maxTrackUpdates;

    @Column(nullable = false)
    private Integer maxStores;
}
