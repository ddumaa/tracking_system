package com.project.tracking_system.dto;

/**
 * @author Dmitriy Anisimov
 * @date 22.03.2025
 */
public record DeliveryFullPeriodStatsDTO(
        String periodLabel,
        long sent,
        long delivered,
        long returned
) {
}