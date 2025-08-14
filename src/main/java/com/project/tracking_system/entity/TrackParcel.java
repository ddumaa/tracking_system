package com.project.tracking_system.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

/**
 * Представляет посылку с трек-номером в системе.
 */

import java.time.ZonedDateTime;
import java.time.ZoneOffset;

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

    @NotBlank(message = "Введите номер посылки")
    @Column(name = "tracking_number", nullable = false)
    private String number;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private GlobalStatus status;

    /**
     * Флаг предварительной регистрации посылки.
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
}

