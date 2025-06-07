package com.project.tracking_system.service.analytics;

import com.project.tracking_system.dto.PeriodStatsDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * @author Dmitriy Anisimov
 * @date 22.03.2025
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DeliveryAnalyticsService {

    private final PeriodDataResolver periodDataResolver;

    /**
     * Aggregates delivery statistics for the given stores within the specified period.
     * <p>
     * Daily statistics are grouped by the provided {@link ChronoUnit} and summed.
     * The method always returns a DTO for each interval between {@code from} and {@code to}.
     * </p>
     *
     * @param storeIds list of store identifiers
     * @param interval grouping interval (days, weeks, months, years)
     * @param from     start date-time in user's zone
     * @param to       end date-time in user's zone
     * @param userZone user's time zone
     * @return list of aggregated statistics ordered by period
     */
    public List<PeriodStatsDTO> getFullPeriodStats(List<Long> storeIds,
                                                   ChronoUnit interval,
                                                   ZonedDateTime from,
                                                   ZonedDateTime to,
                                                   ZoneId userZone) {
        // Delegate to resolver which chooses optimal data source
        return periodDataResolver.resolve(storeIds, interval, from, to, userZone);
    }

}