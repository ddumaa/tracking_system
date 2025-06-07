package com.project.tracking_system.dto;

/**
 * Statistics aggregated for a particular period.
 */
public record PeriodStatsDTO(
        String periodLabel,
        long sent,
        long delivered,
        long returned,
        PeriodStatsSource source
) {}
