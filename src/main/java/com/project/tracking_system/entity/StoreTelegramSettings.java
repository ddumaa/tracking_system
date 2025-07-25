package com.project.tracking_system.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import com.project.tracking_system.entity.BuyerStatus;
import com.project.tracking_system.entity.StoreTelegramTemplate;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Настройки Telegram для магазина.
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "tb_store_telegram_settings")
public class StoreTelegramSettings {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "store_id", nullable = false, unique = true)
    @JsonIgnore
    private Store store;

    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;

    @Column(name = "reminder_start_after_days", nullable = false)
    private int reminderStartAfterDays = 3;

    @Column(name = "reminder_repeat_interval_days", nullable = false)
    private int reminderRepeatIntervalDays = 2;

    @Column(name = "reminder_template", columnDefinition = "TEXT")
    private String reminderTemplate;


    @Column(name = "reminders_enabled", nullable = false)
    private boolean remindersEnabled = false;

    @OneToMany(mappedBy = "settings", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @JsonIgnore
    private List<StoreTelegramTemplate> templates = new ArrayList<>();

    /**
     * Возвращает карту шаблонов сообщений.
     *
     * @return сопоставление статуса и шаблона
     */
    public Map<BuyerStatus, String> getTemplatesMap() {
        Map<BuyerStatus, String> map = new EnumMap<>(BuyerStatus.class);
        for (StoreTelegramTemplate t : templates) {
            map.put(t.getStatus(), t.getTemplate());
        }
        return map;
    }
}
