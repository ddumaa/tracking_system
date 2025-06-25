package com.project.tracking_system.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.ZonedDateTime;

/**
 * @author Dmitriy Anisimov
 * @date 10.03.2025
 */
@Entity
@Table(name = "tb_user_subscriptions")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class UserSubscription {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subscription_plan_id")
    private SubscriptionPlan subscriptionPlan;

    @Column(name = "subscription_end_date")
    private ZonedDateTime subscriptionEndDate;

    @Column(name = "auto_update_enabled", nullable = false)
    private boolean autoUpdateEnabled = true;

    @Column(name = "update_count")
    private int updateCount = 0;

    @Column(name = "reset_date")
    private LocalDate resetDate;

    public void checkAndResetLimits() {
        LocalDate today = LocalDate.now();
        if (!today.equals(resetDate)) {
            this.updateCount = 0;
            this.resetDate = today;
        }
    }
}