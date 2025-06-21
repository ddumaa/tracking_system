package com.project.tracking_system.entity;

import jakarta.persistence.*;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import java.util.Objects;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;

@Entity
@Table(name = "password_reset_token")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class PasswordResetToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false, unique = true)
    private String token;

    @Column(nullable = false)
    private ZonedDateTime expirationDate;


    public PasswordResetToken(String email, String token) {
        this.email = email;
        this.token = token;
        this.expirationDate = ZonedDateTime.now(ZoneOffset.UTC).plusHours(1);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PasswordResetToken that = (PasswordResetToken) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

}