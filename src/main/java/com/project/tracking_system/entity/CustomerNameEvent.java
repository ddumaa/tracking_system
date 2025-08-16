package com.project.tracking_system.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.ZonedDateTime;

/**
 * Событие попытки изменения ФИО покупателя.
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "tb_customer_name_events")
public class CustomerNameEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @Column(name = "old_name")
    private String oldName;

    @Column(name = "new_name", nullable = false)
    private String newName;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private CustomerNameEventStatus status = CustomerNameEventStatus.ACTIVE;

    @Column(name = "created_at", nullable = false)
    private ZonedDateTime createdAt;
}
