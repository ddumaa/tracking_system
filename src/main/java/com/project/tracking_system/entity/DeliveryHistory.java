package com.project.tracking_system.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.ZonedDateTime;

/**
 * @author Dmitriy Anisimov
 * @date 15.03.2025
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "tb_delivery_history")
public class DeliveryHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "track_parcel_id", nullable = true)
    private TrackParcel trackParcel;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "store_id", nullable = false)
    private Store store;

    @Enumerated(EnumType.STRING)
    @Column(name = "postal_service", nullable = false)
    private PostalServiceType postalService;

    /**
     * Дата предварительной регистрации посылки.
     */
    @Column(name = "registration_date")
    private ZonedDateTime registrationDate;

    @Column(name = "send_date")
    private ZonedDateTime sendDate;

    @Column(name = "arrived_date")
    private ZonedDateTime arrivedDate;

    @Column(name = "received_date")
    private ZonedDateTime receivedDate;

    @Column(name = "returned_date")
    private ZonedDateTime returnedDate;

    public DeliveryHistory(TrackParcel trackParcel,
                           Store store,
                           PostalServiceType postalService,
                           ZonedDateTime registrationDate,
                           ZonedDateTime sendDate,
                           ZonedDateTime receivedDate,
                           ZonedDateTime returnedDate) {
        this.trackParcel = trackParcel;
        this.store = store;
        this.postalService = postalService;
        this.registrationDate = registrationDate;
        this.sendDate = sendDate;
        this.receivedDate = receivedDate;
        this.returnedDate = returnedDate;
    }

}