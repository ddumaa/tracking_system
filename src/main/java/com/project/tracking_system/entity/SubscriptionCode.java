package com.project.tracking_system.entity;

import lombok.Getter;

/**
 * @author Dmitriy Anisimov
 * @date 19.06.2025
 */
@Getter
public enum SubscriptionCode {
    FREE("Бесплатный"),
    PREMIUM("Премиум");

    private final String displayName;

    SubscriptionCode(String displayName) {
        this.displayName = displayName;
    }

}