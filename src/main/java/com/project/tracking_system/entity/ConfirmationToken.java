package com.project.tracking_system.entity;

import jakarta.persistence.*;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;

@Data
@Entity
@Table(name = "confirmation_token")
@AllArgsConstructor
@NoArgsConstructor
public class ConfirmationToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;

    private String confirmationCode;

    @Column(nullable = false)
    private ZonedDateTime createdAt;

    public ConfirmationToken(String email, String confirmationCode) {
        this.email = email;
        this.confirmationCode = confirmationCode;
        this.createdAt = ZonedDateTime.now(ZoneOffset.UTC);
    }

}