package com.project.tracking_system.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;


import java.time.LocalDateTime;

@Data
@Entity
@AllArgsConstructor
@NoArgsConstructor
public class ConfirmationToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String email;
    private String confirmationCode;
    private LocalDateTime createdAt;

    public ConfirmationToken(String email, String confirmationCode) {
        this.email = email;
        this.confirmationCode = confirmationCode;
        this.createdAt = LocalDateTime.now();
    }
}