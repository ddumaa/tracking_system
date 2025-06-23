package com.project.tracking_system.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Шаблон Telegram-сообщения для конкретного статуса.
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "tb_store_telegram_templates",
       uniqueConstraints = @UniqueConstraint(columnNames = {"settings_id", "status"}))
public class StoreTelegramTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "settings_id", nullable = false)
    private StoreTelegramSettings settings;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private BuyerStatus status;

    @Column(name = "template", nullable = false, columnDefinition = "text")
    private String template;
}
