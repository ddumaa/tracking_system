package com.project.tracking_system.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @author Dmitriy Anisimov
 * @date 21.03.2025
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserProfileDTO {

    private String email;
    private String timezone;
    private String subscriptionCode;
    private String subscriptionEndDate;

    public String getSubscriptionDisplayName() {
        return subscriptionCode != null ? subscriptionCode : "Без подписки";
    }
}