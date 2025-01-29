package com.project.tracking_system.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "tb_users")
public class User implements UserDetails {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "email", nullable = false, unique = true)
    @NotBlank(message = "Введите адрес электронной почты")
    @Email(message = "Email должен быть корректным")
    private String email;

    @NotBlank(message = "Введите пароль")
    private String password;

    @Column(name = "evropost_username")
    private String evropostUsername;

    @Column(name = "evropost_password")
    private String evropostPassword;

    @Column(name = "jwt_token", length = 512)
    private String jwtToken;

    @Column(name = "service_number")
    private String serviceNumber;

    @Column(name = "use_custom_credentials", nullable = false)
    private Boolean useCustomCredentials = false;

    @Column(name = "token_created_at")
    private LocalDateTime tokenCreatedAt;

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<TrackParcel> trackParcels = new ArrayList<>();

    @OneToOne(mappedBy = "user", cascade = CascadeType.ALL)
    private LoginAttempt loginAttempt;

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(); // Верни роли, если используешь
    }

    @Override
    public String getPassword() {
        return this.password;
    }

    @Override
    public String getUsername() {
        return this.email;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        User user = (User) o;
        return Objects.equals(id, user.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}