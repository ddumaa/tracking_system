package com.project.tracking_system.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * DTO плана подписки с вложенными лимитами.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SubscriptionPlanDTO {
    /**
     * Идентификатор тарифного плана.
     */
    private Long id;
    private String code;
    private String name;
    private String description;
    private BigDecimal price;
    private Integer durationDays;
    private Boolean active;
    private BigDecimal monthlyPrice;
    private BigDecimal annualPrice;
    private SubscriptionLimitsDTO limits = new SubscriptionLimitsDTO();
}