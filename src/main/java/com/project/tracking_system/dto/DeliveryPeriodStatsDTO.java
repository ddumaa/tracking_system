package com.project.tracking_system.dto;

/**
 * @author Dmitriy Anisimov
 * @date 22.03.2025
 */
public record DeliveryPeriodStatsDTO(
        String periodLabel, // Например: "2025-03-01", "неделя 12", "март 2025"
        long totalSent
) {

}