package com.project.tracking_system.dto;

import com.project.tracking_system.entity.SubscriptionCode;
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
    private SubscriptionCode subscriptionCode;
    private String subscriptionEndDate;

    public String getSubscriptionDisplayName() {
        return subscriptionCode != null ? subscriptionCode.getDisplayName() : "Без подписки";
    }
}