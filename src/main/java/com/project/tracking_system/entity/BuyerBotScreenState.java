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

/**
 * Состояние якорного сообщения покупателя в Telegram.
 * <p>
 * Хранит идентификатор последнего сообщения с инлайн-кнопками и экран,
 * который должен отображаться при его перерисовке.
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
}

