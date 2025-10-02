package com.project.tracking_system.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;

/**
 * Эпизод заказа объединяет все посылки, связанные с одним клиентским обращением.
 * <p>
 * В рамках эпизода может создаваться несколько посылок, например, при обмене.
 * Сущность фиксирует ключевые даты и финальный исход эпизода.
 * </p>
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "tb_order_episodes")
public class OrderEpisode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Магазин, в котором оформлен эпизод.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "store_id", nullable = false)
    private Store store;

    /**
     * Покупатель, для которого ведётся эпизод.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id")
    private Customer customer;

    /**
     * Финальный исход эпизода. Для открытых эпизодов хранится значение {@link OrderFinalOutcome#OPEN}.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "final_outcome")
    private OrderFinalOutcome finalOutcome = OrderFinalOutcome.OPEN;

    /**
     * Время открытия эпизода.
     */
    @Column(name = "started_at", nullable = false, updatable = false)
    private ZonedDateTime startedAt = ZonedDateTime.now(ZoneOffset.UTC);

    /**
     * Время закрытия эпизода. Проставляется только для финальных исходов.
     */
    @Column(name = "closed_at")
    private ZonedDateTime closedAt;

    /**
     * Количество выполненных обменов в рамках эпизода.
     */
    @Column(name = "exchanges_count", nullable = false)
    private int exchangesCount = 0;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    /**
     * Проверяет, открыт ли эпизод.
     *
     * @return {@code true}, если финальный исход ещё не зафиксирован
     */
    public boolean isOpen() {
        return finalOutcome == null || finalOutcome == OrderFinalOutcome.OPEN;
    }
}
