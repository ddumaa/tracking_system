package com.project.tracking_system.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Покупатель, оформляющий заказы в системе.
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "tb_customers")
public class Customer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "phone", nullable = false, unique = true)
    private String phone;

    @Column(name = "sent_count", nullable = false)
    private int sentCount = 0;

    @Column(name = "picked_up_count", nullable = false)
    private int pickedUpCount = 0;

    @Enumerated(EnumType.STRING)
    @Column(name = "reputation", nullable = false)
    private BuyerReputation reputation = BuyerReputation.NEUTRAL;

    /**
     * Пересчитать репутацию покупателя на основе количества отправленных
     * и забранных им посылок.
     */
    public void recalculateReputation() {
        if (sentCount == 0) {
            reputation = BuyerReputation.NEUTRAL;
            return;
        }
        double rate = (double) pickedUpCount / sentCount;
        if (rate >= 0.8) {
            reputation = BuyerReputation.RELIABLE;
        } else if (rate <= 0.3) {
            reputation = BuyerReputation.UNRELIABLE;
        } else {
            reputation = BuyerReputation.NEUTRAL;
        }
    }
}
