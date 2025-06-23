package com.project.tracking_system.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

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
    private String subscriptionEndDate;
    private boolean autoUpdateEnabled;

    public String getSubscriptionDisplayName() {
        return subscriptionCode != null ? subscriptionCode : "Без подписки";
    }
}