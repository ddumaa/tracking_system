package com.project.tracking_system.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

/**
 * Информация о магазине для административной панели.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class StoreAdminInfoDTO {
    private Long id;
    private String name;
    private String ownerEmail;
    private boolean telegramEnabled;
    private boolean remindersEnabled;
    private String subscriptionPlan;
}
