package com.project.tracking_system.service.analytics;

import com.project.tracking_system.dto.PeriodStatsDTO;
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

    /**
     * Агрегирует данные для круговой диаграммы по переданному списку статистик.
     *
     * @param stats список {@link StoreStatistics} по выбранным магазинам
     * @return карта со следующими ключами:
     * <ul>
     *     <li>{@code delivered} – количество доставленных отправлений</li>
     *     <li>{@code returned} – количество возвращённых отправлений</li>
     *     <li>{@code inTransit} – отправления, находящиеся в пути,
     *     вычисляется как {@code totalSent - delivered - returned}.
     *     Значение не может быть отрицательным</li>
     * </ul>
     */
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

    /**
     * Builds chart data for the selected period.
     *
     * @param storeIds ids of stores to aggregate
     * @param interval requested interval (days, weeks, months, years)
     * @param userZone user time zone
     * @return map with labels and series values
     */
    public Map<String, Object> getFullPeriodStatsChart(List<Long> storeIds,
                                                       ChronoUnit interval,
                                                       ZoneId userZone) {
        ZonedDateTime now = ZonedDateTime.now(userZone).truncatedTo(ChronoUnit.DAYS);
        ZonedDateTime from = switch (interval) {
            case DAYS -> now.minusDays(7);
            case WEEKS -> now.minusWeeks(4);
            case MONTHS -> now.minusMonths(6);
            case YEARS -> now.minusYears(5);
            default -> throw new IllegalArgumentException("Unsupported interval: " + interval);
        };
        ZonedDateTime to = now;

        List<PeriodStatsDTO> list = deliveryAnalyticsService.getFullPeriodStats(
                storeIds, interval, from, to, userZone
        );

        return Map.of(
                "labels", list.stream().map(PeriodStatsDTO::periodLabel).toList(),
                "sent", list.stream().map(PeriodStatsDTO::sent).toList(),
                "delivered", list.stream().map(PeriodStatsDTO::delivered).toList(),
                "returned", list.stream().map(PeriodStatsDTO::returned).toList()
        );
    }


}