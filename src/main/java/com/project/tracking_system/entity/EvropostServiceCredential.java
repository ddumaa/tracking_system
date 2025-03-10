package com.project.tracking_system.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.ZonedDateTime;

/**
 * @author Dmitriy Anisimov
 * @date 09.03.2025
 */

@Entity
@Table(name = "tb_evropost_service_credentials")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class EvropostServiceCredential {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "username")
    private String username;

    @Column(name = "password")
    private String password;

    @Column(name = "jwt_token", length = 512)
    private String jwtToken;

    @Column(name = "service_number")
    private String serviceNumber;

    @Column(name = "use_custom_credentials", nullable = false)
    private Boolean useCustomCredentials = false;

    @Column(name = "token_created_at")
    private ZonedDateTime tokenCreatedAt;


}