package com.project.tracking_system.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.ZonedDateTime;

/**
 * Состояние якорного сообщения покупателя в Telegram.
 * <p>
 * Хранит идентификатор последнего сообщения с инлайн-кнопками и экран,
 * который должен отображаться при его перерисовке, а также сценарное состояние диалога.
 * </p>
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "tb_buyer_bot_screen_states")
public class BuyerBotScreenState {

    /**
     * Идентификатор чата покупателя в Telegram.
     */
    @Id
    @Column(name = "chat_id")
    private Long chatId;

    /**
     * Идентификатор якорного сообщения с инлайн-кнопками.
     */
    @Column(name = "anchor_message_id")
    private Integer anchorMessageId;

    /**
     * Последний экран, отрисованный ботом для покупателя.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "last_screen")
    private BuyerBotScreen lastScreen;

    /**
     * Текущее сценарное состояние диалога покупателя.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "chat_state")
    private BuyerChatState chatState;

    /**
     * Признак того, что клавиатура меню скрыта у пользователя.
     */
    @Column(name = "keyboard_hidden")
    private Boolean keyboardHidden;

    /**
     * Признак того, что запрос контакта уже был отправлен покупателю.
     */
    @Column(name = "contact_request_sent")
    private Boolean contactRequestSent;

    /**
     * Последовательность экранов, составляющая путь навигации пользователя.
     */
    @Column(name = "navigation_path")
    private String navigationPath;

    /**
     * Посылка, по которой пользователь начал оформление возврата.
     */
    @Column(name = "return_parcel_id")
    private Long returnParcelId;

    /**
     * Трек-номер исходной посылки, выводимый в подсказках.
     */
    @Column(name = "return_parcel_track", length = 64)
    private String returnParcelTrack;

    /**
     * Указанная пользователем причина возврата.
     */
    @Column(name = "return_reason", length = 255)
    private String returnReason;

    /**
     * Дополнительный комментарий к заявке на возврат.
     */
    @Column(name = "return_comment", columnDefinition = "TEXT")
    private String returnComment;

    /**
     * Дата, когда пользователь решил оформить возврат.
     */
    @Column(name = "return_requested_at")
    private ZonedDateTime returnRequestedAt;

    /**
     * Трек-номер обратной отправки.
     */
    @Column(name = "return_reverse_track", length = 64)
    private String returnReverseTrack;

    /**
     * Идемпотентный ключ заявки на возврат.
     */
    @Column(name = "return_idempotency_key", length = 64)
    private String returnIdempotencyKey;
}

