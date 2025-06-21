package com.project.tracking_system.dto;

import com.project.tracking_system.entity.Role;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

/**
 * @author Dmitriy Anisimov
 * @date 27.02.2025
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class UserDetailsAdminInfoDTO {
    private Long id;
    private String email;
    private Role role;
    /** Название текущего тарифного плана */
    private String subscriptionPlanName;
    private String subscriptionPlanCode;
    private String subscriptionEndDate;
}
