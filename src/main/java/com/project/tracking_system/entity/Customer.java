package com.project.tracking_system.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.ZonedDateTime;

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

    @Column(name = "full_name")
    private String fullName;

    @Enumerated(EnumType.STRING)
    @Column(name = "name_source", nullable = false)
    private NameSource nameSource = NameSource.MERCHANT_PROVIDED;

    @Column(name = "name_updated_at")
    private ZonedDateTime nameUpdatedAt;

    @Column(name = "sent_count", nullable = false)
    private int sentCount = 0;

    @Column(name = "picked_up_count", nullable = false)
    private int pickedUpCount = 0;

    @Column(name = "returned_count", nullable = false)
    private int returnedCount = 0;

    @Column(name = "telegram_chat_id")
    private Long telegramChatId;

    @Column(name = "telegram_confirmed", nullable = false)
    private boolean telegramConfirmed = false;

    @Column(name = "notifications_enabled", nullable = false)
    private boolean notificationsEnabled = true;

    @Enumerated(EnumType.STRING)
    @Column(name = "reputation", nullable = false)
    private BuyerReputation reputation = BuyerReputation.NEW;

    /**
     * Дата и время последней активности покупателя в Telegram.
     */
    @Column(name = "last_active_at")
    private ZonedDateTime lastActiveAt;

    /**
     * Версия записи для реализации оптимистичной блокировки.
     */
    @Version
    @Column(name = "version", nullable = false)
    private long version;

    /**
     * Пересчитать репутацию покупателя на основе завершённых заказов.
     * <p>
     * Репутация "Формируется" присваивается, если суммарно обработано
     * меньше трёх посылок. Далее оценивается доля забранных заказов
     * относительно всех завершённых (забранных + возвращённых).
     * </p>
     */
    public void recalculateReputation() {
        int finished = pickedUpCount + returnedCount;
        if (finished < 3) {
            this.reputation = BuyerReputation.NEW;
            return;
        }
        double ratio = (double) pickedUpCount / finished;
        if (ratio >= 0.8) {
            this.reputation = BuyerReputation.RELIABLE;
        } else if (ratio >= 0.5) {
            this.reputation = BuyerReputation.NEUTRAL;
        } else {
            this.reputation = BuyerReputation.UNRELIABLE;
        }
    }
}
