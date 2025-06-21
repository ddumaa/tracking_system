package com.project.tracking_system.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * DTO плана подписки с вложенными лимитами.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SubscriptionPlanDTO {
    /**
     * Идентификатор тарифного плана.
     */
    private Long id;
    private String code;
    private String name;
    private Boolean active;
    private BigDecimal monthlyPrice;
    private BigDecimal annualPrice;
    private SubscriptionLimitsDTO limits = new SubscriptionLimitsDTO();
}