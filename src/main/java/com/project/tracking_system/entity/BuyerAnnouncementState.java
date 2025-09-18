package com.project.tracking_system.entity;

import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.HashSet;
import java.util.Set;

/**
 * Состояние рассылки объявлений для покупателя в Telegram.
 * <p>
 * Позволяет хранить идентификатор текущего уведомления и признак того,
 * что пользователь уже ознакомился с его содержимым. Дополнительно
 * сохраняется история просмотренных уведомлений, чтобы не дублировать
 * показ при повторных рассылках.
 * </p>
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "tb_buyer_announcement_states")
public class BuyerAnnouncementState {

    /**
     * Уникальный идентификатор чата покупателя в Telegram.
     */
    @Id
    @Column(name = "chat_id")
    private Long chatId;

    /**
     * Идентификатор актуального уведомления, которое должно быть показано пользователю.
     */
    @Column(name = "current_notification_id")
    private Long currentNotificationId;

    /**
     * Признак того, что пользователь уже просмотрел текущее уведомление.
     */
    @Column(name = "announcement_seen", nullable = false)
    private Boolean announcementSeen = Boolean.FALSE;

    /**
     * Сообщение, содержащее текст объявления и управляющие элементы.
     */
    @Column(name = "anchor_message_id")
    private Integer anchorMessageId;

    /**
     * Набор идентификаторов объявлений, уже показанных пользователю ранее.
     */
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "tb_buyer_seen_announcements", joinColumns = @JoinColumn(name = "chat_id"))
    @Column(name = "notification_id")
    private Set<Long> seenNotificationIds = new HashSet<>();
}

