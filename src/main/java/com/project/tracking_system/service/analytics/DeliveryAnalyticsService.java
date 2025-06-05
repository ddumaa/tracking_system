package com.project.tracking_system.service.analytics;

import com.project.tracking_system.dto.DeliveryFullPeriodStatsDTO;
import com.project.tracking_system.entity.StoreStatistics;
import com.project.tracking_system.repository.StoreAnalyticsRepository;
import com.project.tracking_system.repository.StoreDailyStatisticsRepository;
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
    private final StoreDailyStatisticsRepository storeDailyStatisticsRepository;
    private final StoreAnalyticsService storeAnalyticsService;

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
    public List<DeliveryFullPeriodStatsDTO> getFullPeriodStats(List<Long> storeIds,
                                                               ChronoUnit interval,
                                                               ZonedDateTime from,
                                                               ZonedDateTime to,
                                                               ZoneId userZone) {

        // Запрашиваем ежедневную статистику выбранных магазинов в указанном диапазоне
        var dailyStats = storeDailyStatisticsRepository
                .findByStoreIdInAndDateBetween(storeIds, from.toLocalDate(), to.toLocalDate());

        // Группируем по нужному интервалу и суммируем счетчики
        var grouped = new java.util.TreeMap<ZonedDateTime, long[]>();
        for (var stat : dailyStats) {
            ZonedDateTime date = stat.getDate().atStartOfDay(userZone);
            ZonedDateTime key = alignToPeriod(date, interval, userZone);
            long[] arr = grouped.computeIfAbsent(key, k -> new long[3]);
            arr[0] += stat.getSent();
            arr[1] += stat.getDelivered();
            arr[2] += stat.getReturned();
        }

        // Формируем итоговый список, включая периоды без данных
        List<DeliveryFullPeriodStatsDTO> result = new java.util.ArrayList<>();
        ZonedDateTime cursor = alignToPeriod(from, interval, userZone);
        ZonedDateTime end = alignToPeriod(to, interval, userZone);
        while (!cursor.isAfter(end)) {
            long[] arr = grouped.getOrDefault(cursor, new long[3]);
            result.add(new DeliveryFullPeriodStatsDTO(
                    formatLabel(cursor, interval),
                    arr[0],
                    arr[1],
                    arr[2]
            ));
            cursor = cursor.plus(1, interval);
        }

        return result;
    }

    private ZonedDateTime alignToPeriod(ZonedDateTime date, ChronoUnit interval, ZoneId zone) {
        ZonedDateTime zoned = date.withZoneSameInstant(zone);
        return switch (interval) {
            case DAYS -> zoned.truncatedTo(ChronoUnit.DAYS);
            case WEEKS -> zoned.with(java.time.DayOfWeek.MONDAY).truncatedTo(ChronoUnit.DAYS);
            case MONTHS -> zoned.withDayOfMonth(1).truncatedTo(ChronoUnit.DAYS);
            case YEARS -> zoned.withDayOfYear(1).truncatedTo(ChronoUnit.DAYS);
            default -> zoned.truncatedTo(ChronoUnit.DAYS);
        };
    }

    private String formatLabel(ZonedDateTime date, ChronoUnit interval) {
        return switch (interval) {
            case DAYS -> date.toLocalDate().toString();
            case WEEKS -> "Week " + date.get(java.time.temporal.IsoFields.WEEK_OF_WEEK_BASED_YEAR);
            case MONTHS -> date.getMonth() + " " + date.getYear();
            case YEARS -> String.valueOf(date.getYear());
            default -> date.toLocalDate().toString();
        };
    }

}