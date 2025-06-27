package com.project.tracking_system.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * DTO c информацией о привязке покупателя к Telegram.
 */
@Getter
@Setter
public class CustomerTelegramLinkDTO {

    /** Идентификатор привязки. */
    private Long id;

    /** Телефон покупателя. */
    private String phone;

    /** Идентификатор Telegram-чата. */
    private Long telegramChatId;

    /** Подтверждён ли Telegram. */
    private boolean telegramConfirmed;

    /** Включены ли уведомления. */
    private boolean notificationsEnabled;
}
