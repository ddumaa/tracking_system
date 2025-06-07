package com.project.tracking_system.service.analytics;

import com.project.tracking_system.dto.PeriodStatsDTO;
import com.project.tracking_system.dto.PeriodStatsSource;
import com.project.tracking_system.entity.StoreDailyStatistics;
import com.project.tracking_system.entity.StoreMonthlyStatistics;
import com.project.tracking_system.entity.StoreWeeklyStatistics;
import com.project.tracking_system.entity.StoreYearlyStatistics;
import com.project.tracking_system.repository.StoreDailyStatisticsRepository;
import com.project.tracking_system.repository.StoreMonthlyStatisticsRepository;
import com.project.tracking_system.repository.StoreWeeklyStatisticsRepository;
import com.project.tracking_system.repository.StoreYearlyStatisticsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.time.temporal.IsoFields;
import java.util.ArrayList;
import java.util.List;

/**
 * Resolves statistics for a date range by checking aggregated tables
 * and falling back to daily records when necessary.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PeriodDataResolver {

    private final StoreDailyStatisticsRepository dailyRepo;
    private final StoreWeeklyStatisticsRepository weeklyRepo;
    private final StoreMonthlyStatisticsRepository monthlyRepo;
    private final StoreYearlyStatisticsRepository yearlyRepo;

    /**
     * Returns statistics for each period between {@code from} and {@code to}.
     * The method uses aggregated tables when available and falls back to
     * daily data when a period has no precomputed entry.
     *
     * @param storeIds list of store identifiers
     * @param interval requested interval
     * @param from     start date-time in user's zone
     * @param to       end date-time in user's zone
     * @param zone     user's time zone
     * @return list of statistics ordered by period
     */
    public List<PeriodStatsDTO> resolve(List<Long> storeIds,
                                        ChronoUnit interval,
                                        ZonedDateTime from,
                                        ZonedDateTime to,
                                        ZoneId zone) {
        ZonedDateTime cursor = alignToPeriod(from, interval, zone);
        ZonedDateTime end = alignToPeriod(to, interval, zone);
        List<PeriodStatsDTO> list = new ArrayList<>();
        while (!cursor.isAfter(end)) {
            list.add(resolveSingle(storeIds, interval, cursor, zone));
            cursor = cursor.plus(1, interval);
        }
        return list;
    }

    private PeriodStatsDTO resolveSingle(List<Long> storeIds,
                                         ChronoUnit interval,
                                         ZonedDateTime start,
                                         ZoneId zone) {
        return switch (interval) {
            case DAYS -> daily(storeIds, start, start, zone);
            case WEEKS -> weekly(storeIds, start, zone);
            case MONTHS -> monthly(storeIds, start, zone);
            case YEARS -> yearly(storeIds, start, zone);
            default -> daily(storeIds, start, start, zone);
        };
    }

    private PeriodStatsDTO daily(List<Long> storeIds,
                                 ZonedDateTime start,
                                 ZonedDateTime end,
                                 ZoneId zone) {
        LocalDate from = start.withZoneSameInstant(zone).toLocalDate();
        LocalDate to = end.withZoneSameInstant(zone).toLocalDate();
        List<StoreDailyStatistics> stats = dailyRepo.findByStoreIdInAndDateBetween(storeIds, from, to);
        long[] totals = new long[3];
        for (StoreDailyStatistics s : stats) {
            totals[0] += s.getSent();
            totals[1] += s.getDelivered();
            totals[2] += s.getReturned();
        }
        return new PeriodStatsDTO(formatLabel(start, ChronoUnit.DAYS),
                totals[0], totals[1], totals[2], PeriodStatsSource.DAILY);
    }

    private PeriodStatsDTO weekly(List<Long> storeIds,
                                  ZonedDateTime start,
                                  ZoneId zone) {
        int week = start.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
        int year = start.get(IsoFields.WEEK_BASED_YEAR);
        boolean complete = true;
        long[] totals = new long[3];
        for (Long id : storeIds) {
            var opt = weeklyRepo.findByStoreIdAndPeriodYearAndPeriodNumber(id, year, week);
            if (opt.isPresent()) {
                StoreWeeklyStatistics s = opt.get();
                totals[0] += s.getSent();
                totals[1] += s.getDelivered();
                totals[2] += s.getReturned();
            } else {
                complete = false;
                break;
            }
        }
        if (complete) {
            return new PeriodStatsDTO(formatLabel(start, ChronoUnit.WEEKS),
                    totals[0], totals[1], totals[2], PeriodStatsSource.WEEKLY);
        }
        ZonedDateTime end = start.plusWeeks(1).minusDays(1);
        return daily(storeIds, start, end, zone);
    }

    private PeriodStatsDTO monthly(List<Long> storeIds,
                                   ZonedDateTime start,
                                   ZoneId zone) {
        int month = start.getMonthValue();
        int year = start.getYear();
        boolean complete = true;
        long[] totals = new long[3];
        for (Long id : storeIds) {
            var opt = monthlyRepo.findByStoreIdAndPeriodYearAndPeriodNumber(id, year, month);
            if (opt.isPresent()) {
                StoreMonthlyStatistics s = opt.get();
                totals[0] += s.getSent();
                totals[1] += s.getDelivered();
                totals[2] += s.getReturned();
            } else {
                complete = false;
                break;
            }
        }
        if (complete) {
            return new PeriodStatsDTO(formatLabel(start, ChronoUnit.MONTHS),
                    totals[0], totals[1], totals[2], PeriodStatsSource.MONTHLY);
        }
        ZonedDateTime end = start.plusMonths(1).minusDays(1);
        return daily(storeIds, start, end, zone);
    }

    private PeriodStatsDTO yearly(List<Long> storeIds,
                                  ZonedDateTime start,
                                  ZoneId zone) {
        int year = start.getYear();
        boolean complete = true;
        long[] totals = new long[3];
        for (Long id : storeIds) {
            var opt = yearlyRepo.findByStoreIdAndPeriodYearAndPeriodNumber(id, year, 1);
            if (opt.isPresent()) {
                StoreYearlyStatistics s = opt.get();
                totals[0] += s.getSent();
                totals[1] += s.getDelivered();
                totals[2] += s.getReturned();
            } else {
                complete = false;
                break;
            }
        }
        if (complete) {
            return new PeriodStatsDTO(formatLabel(start, ChronoUnit.YEARS),
                    totals[0], totals[1], totals[2], PeriodStatsSource.YEARLY);
        }
        ZonedDateTime end = start.plusYears(1).minusDays(1);
        return daily(storeIds, start, end, zone);
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
            case WEEKS -> "Week " + date.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
            case MONTHS -> date.getMonth() + " " + date.getYear();
            case YEARS -> String.valueOf(date.getYear());
            default -> date.toLocalDate().toString();
        };
    }
}
