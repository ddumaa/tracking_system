package com.project.tracking_system.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

import java.time.ZonedDateTime;

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

    @Column(name = "data", nullable = false)
    private ZonedDateTime data;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "store_id", nullable = false)
    private Store store;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @OneToOne(mappedBy = "trackParcel", cascade = CascadeType.ALL, orphanRemoval = true)
    private DeliveryHistory deliveryHistory;
}