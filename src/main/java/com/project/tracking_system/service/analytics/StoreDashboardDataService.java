package com.project.tracking_system.service.analytics;

import com.project.tracking_system.dto.DeliveryFullPeriodStatsDTO;
import com.project.tracking_system.entity.StoreStatistics;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

/**
 * @author Dmitriy Anisimov
 * @date 22.03.2025
 */
@Service
@RequiredArgsConstructor
public class StoreDashboardDataService {

    private final DeliveryAnalyticsService deliveryAnalyticsService;

    public Map<String, Object> calculatePieData(List<StoreStatistics> stats) {
        int delivered = stats.stream().mapToInt(StoreStatistics::getTotalDelivered).sum();
        int returned = stats.stream().mapToInt(StoreStatistics::getTotalReturned).sum();
        int sent = stats.stream().mapToInt(StoreStatistics::getTotalSent).sum();
        int inTransit = sent - delivered - returned;

        return Map.of(
                "delivered", delivered,
                "returned", returned,
                "inTransit", Math.max(inTransit, 0)
        );
    }

    public Map<String, Object> getFullPeriodStatsChart(List<Long> storeIds,
                                                       ChronoUnit interval,
                                                       ZoneId userZone) {
        ZonedDateTime now = ZonedDateTime.now(userZone);
        ZonedDateTime from = switch (interval) {
            case DAYS -> now.minusDays(7);
            case WEEKS -> now.minusWeeks(4);
            case MONTHS -> now.minusMonths(6);
            default -> throw new IllegalArgumentException("Unsupported interval: " + interval);
        };
        ZonedDateTime to = now;

        List<DeliveryFullPeriodStatsDTO> list = deliveryAnalyticsService.getFullPeriodStats(
                storeIds, interval, from, to, userZone
        );

        return Map.of(
                "labels", list.stream().map(DeliveryFullPeriodStatsDTO::periodLabel).toList(),
                "sent", list.stream().map(DeliveryFullPeriodStatsDTO::sent).toList(),
                "delivered", list.stream().map(DeliveryFullPeriodStatsDTO::delivered).toList(),
                "returned", list.stream().map(DeliveryFullPeriodStatsDTO::returned).toList()
        );
    }


}