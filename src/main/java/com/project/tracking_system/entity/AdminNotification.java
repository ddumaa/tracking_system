package com.project.tracking_system.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Административное уведомление, отображаемое пользователям панели.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "tb_admin_notifications")
public class AdminNotification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "title", nullable = false, length = 255)
    private String title;

    @ElementCollection
    @CollectionTable(name = "tb_admin_notification_lines", joinColumns = @JoinColumn(name = "notification_id"))
    @Column(name = "line", nullable = false, columnDefinition = "TEXT")
    private List<String> bodyLines = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private AdminNotificationStatus status = AdminNotificationStatus.INACTIVE;

    @Column(name = "reset_requested", nullable = false)
    private boolean resetRequested = true;

    @Column(name = "created_at", nullable = false)
    private ZonedDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private ZonedDateTime updatedAt;

    /**
     * Устанавливает временные метки перед сохранением новой записи.
     */
    @PrePersist
    public void onCreate() {
        ZonedDateTime now = ZonedDateTime.now(ZoneOffset.UTC);
        this.createdAt = now;
        this.updatedAt = now;
    }

    /**
     * Обновляет временную метку перед изменением записи.
     */
    @PreUpdate
    public void onUpdate() {
        this.updatedAt = ZonedDateTime.now(ZoneOffset.UTC);
    }
}
