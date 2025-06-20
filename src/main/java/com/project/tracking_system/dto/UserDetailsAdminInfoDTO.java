package com.project.tracking_system.dto;

import com.project.tracking_system.entity.Role;
import com.project.tracking_system.entity.SubscriptionCode;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @author Dmitriy Anisimov
 * @date 27.02.2025
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class UserDetailsAdminInfoDTO {
    private Long id;
    private String email;
    private Role role;
    private SubscriptionCode subscriptionPlanCode;
    private String subscriptionEndDate;
}
