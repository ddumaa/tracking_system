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
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.time.temporal.IsoFields;
import java.util.ArrayList;
import java.util.List;

/**
 * Сервис для получения статистики за период с использованием агрегированных таблиц
 * и при необходимости с падением до ежедневных записей.
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
     * Возвращает статистику за каждый период между {@code from} и {@code to}.
     * При наличии агрегированных данных используется их таблица,
     * в противном случае производится расчёт по ежедневным записям.
     *
     * @param storeIds список идентификаторов магазинов
     * @param interval запрашиваемый интервал
     * @param from     дата начала в часовом поясе пользователя
     * @param to       дата окончания в часовом поясе пользователя
     * @param zone     часовой пояс пользователя
     * @return список статистик, отсортированный по периоду
     */
    @Transactional(readOnly = true)
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
        List<StoreWeeklyStatistics> stats =
                weeklyRepo.findByStoreIdInAndPeriodYearAndPeriodNumber(storeIds, year, week);
        if (stats.size() == storeIds.size()) {
            long[] totals = new long[3];
            for (StoreWeeklyStatistics s : stats) {
                totals[0] += s.getSent();
                totals[1] += s.getDelivered();
                totals[2] += s.getReturned();
            }
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
        List<StoreMonthlyStatistics> stats =
                monthlyRepo.findByStoreIdInAndPeriodYearAndPeriodNumber(storeIds, year, month);
        if (stats.size() == storeIds.size()) {
            long[] totals = new long[3];
            for (StoreMonthlyStatistics s : stats) {
                totals[0] += s.getSent();
                totals[1] += s.getDelivered();
                totals[2] += s.getReturned();
            }
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
        List<StoreYearlyStatistics> stats =
                yearlyRepo.findByStoreIdInAndPeriodYearAndPeriodNumber(storeIds, year, 1);
        if (stats.size() == storeIds.size()) {
            long[] totals = new long[3];
            for (StoreYearlyStatistics s : stats) {
                totals[0] += s.getSent();
                totals[1] += s.getDelivered();
                totals[2] += s.getReturned();
            }
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
