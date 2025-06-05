package com.project.tracking_system.service.analytics;

import com.project.tracking_system.dto.DeliveryFullPeriodStatsDTO;
import com.project.tracking_system.entity.StoreStatistics;
import com.project.tracking_system.repository.StoreAnalyticsRepository;
import com.project.tracking_system.service.analytics.StoreAnalyticsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;

/**
 * @author Dmitriy Anisimov
 * @date 22.03.2025
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DeliveryAnalyticsService {

    private final StoreAnalyticsRepository storeAnalyticsRepository;
    private final StoreAnalyticsService storeAnalyticsService;

    public List<DeliveryFullPeriodStatsDTO> getFullPeriodStats(List<Long> storeIds,
                                                               ChronoUnit interval,
                                                               ZonedDateTime from,
                                                               ZonedDateTime to,
                                                               ZoneId userZone) {

        List<StoreStatistics> stats = storeAnalyticsRepository.findByStoreIdIn(storeIds);

        long totalSent = stats.stream().mapToLong(StoreStatistics::getTotalSent).sum();
        long totalDelivered = stats.stream().mapToLong(StoreStatistics::getTotalDelivered).sum();
        long totalReturned = stats.stream().mapToLong(StoreStatistics::getTotalReturned).sum();

        String label = formatLabel(alignToPeriod(from, interval, userZone), interval);

        return Collections.singletonList(
                new DeliveryFullPeriodStatsDTO(label, totalSent, totalDelivered, totalReturned)
        );
    }

    private ZonedDateTime alignToPeriod(ZonedDateTime date, ChronoUnit interval, ZoneId zone) {
        ZonedDateTime zoned = date.withZoneSameInstant(zone);
        return switch (interval) {
            case DAYS -> zoned.truncatedTo(ChronoUnit.DAYS);
            case WEEKS -> zoned.with(java.time.DayOfWeek.MONDAY).truncatedTo(ChronoUnit.DAYS);
            case MONTHS -> zoned.withDayOfMonth(1).truncatedTo(ChronoUnit.DAYS);
            default -> zoned.truncatedTo(ChronoUnit.DAYS);
        };
    }

    private String formatLabel(ZonedDateTime date, ChronoUnit interval) {
        return switch (interval) {
            case DAYS -> date.toLocalDate().toString();
            case WEEKS -> "Week " + date.get(java.time.temporal.IsoFields.WEEK_OF_WEEK_BASED_YEAR);
            case MONTHS -> date.getMonth() + " " + date.getYear();
            default -> date.toLocalDate().toString();
        };
    }

}