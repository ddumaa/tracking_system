package com.project.tracking_system.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.AssertTrue;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.ZonedDateTime;
import java.time.ZoneOffset;

/**
 * Представляет посылку с трек-номером в системе.
 */

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "tb_track_parcels")
public class TrackParcel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Трек-номер посылки. Должен быть указан, когда посылка не
     * предварительно зарегистрирована. При включённом флаге
     * предварительной регистрации значение может отсутствовать
     * или быть пустым.
     */
    @Column(name = "tracking_number", nullable = true)
    private String number;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private GlobalStatus status;

    /**
     * Направление движения посылки относительно покупателя.
     * Значение используется для разделения исходящих отправлений и возвратов.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "route_direction", nullable = false)
    private RouteDirection routeDirection = RouteDirection.TO_CUSTOMER;

    /**
     * Флаг предварительной регистрации посылки. Если установлен,
     * поле {@link #number} может быть пустым.
     */
    @Column(name = "pre_registered", nullable = false)
    private boolean preRegistered = false;

    /**
     * Дата последнего статуса либо время предварительной регистрации.
     * Заполняется автоматически при создании записи.
     */
    @CreationTimestamp
    @Column(name = "timestamp", nullable = false)
    private ZonedDateTime timestamp;

    /**
     * Дата последнего обновления трека в UTC.
     */
    @Column(name = "last_update", nullable = false)
    private ZonedDateTime lastUpdate = ZonedDateTime.now(ZoneOffset.UTC);

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "store_id", nullable = false)
    private Store store;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id")
    private Customer customer;

    @OneToOne(mappedBy = "trackParcel", cascade = {}, orphanRemoval = false)
    private DeliveryHistory deliveryHistory;

    @Column(name = "included_in_statistics", nullable = false)
    private boolean includedInStatistics = false;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    /**
     * Устанавливает статус посылки и синхронизирует флаг предварительной регистрации.
     * Инвариант: статус {@link GlobalStatus#PRE_REGISTERED} строго соответствует
     * значению {@code preRegistered = true}.
     *
     * @param status новый статус посылки
     */
    public void setStatus(GlobalStatus status) {
        this.status = status;
        this.preRegistered = status == GlobalStatus.PRE_REGISTERED;
    }

    /**
     * Устанавливает флаг предварительной регистрации и при необходимости
     * корректирует статус.
     * Инвариант: статус {@link GlobalStatus#PRE_REGISTERED} строго соответствует
     * значению {@code preRegistered = true}.
     *
     * @param preRegistered флаг предварительной регистрации
     */
    public void setPreRegistered(boolean preRegistered) {
        this.preRegistered = preRegistered;
        if (preRegistered) {
            this.status = GlobalStatus.PRE_REGISTERED;
        } else if (this.status == GlobalStatus.PRE_REGISTERED) {
            this.status = GlobalStatus.UNKNOWN_STATUS;
        }
    }

    /**
     * Проверяет, что трек-номер указан, если посылка не
     * предварительно зарегистрирована.
     * Инвариант: при {@code preRegistered = false} значение
     * {@link #number} не должно быть пустым.
     *
     * @return {@code true}, если инвариант соблюдён
     */
    @AssertTrue(message = "Трек-номер обязателен, если посылка не предварительно зарегистрирована")
    private boolean isTrackingNumberValid() {
        return preRegistered || (number != null && !number.isBlank());
    }
}

