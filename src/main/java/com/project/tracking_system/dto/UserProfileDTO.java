package com.project.tracking_system.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

import com.project.tracking_system.dto.SubscriptionPlanViewDTO;

/**
 * @author Dmitriy Anisimov
 * @date 21.03.2025
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UserProfileDTO {

    private String email;
    private String timezone;
    private String subscriptionCode;
    private String subscriptionName;
    private String subscriptionEndDate;
    private boolean autoUpdateEnabled;
    /**
     * Детальная информация о текущем тарифном плане пользователя.
     */
    private SubscriptionPlanViewDTO planDetails;
    /** Количество сохранённых треков пользователя. */
    private int savedTracksUsed;
    /** Количество обновлений треков, выполненных сегодня. */
    private int trackUpdatesUsed;
    /** Количество созданных магазинов пользователя. */
    private int storesUsed;

    public String getSubscriptionDisplayName() {
        if (subscriptionName != null) {
            return subscriptionName;
        }
        return subscriptionCode != null ? subscriptionCode : "Без подписки";
    }
}