package com.project.tracking_system.service.analytics;

import com.project.tracking_system.entity.*;
import com.project.tracking_system.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.IsoFields;
import java.time.temporal.ChronoField;
import java.util.List;

/**
 * Агрегирует ежедневную статистику в недельные, месячные и годовые таблицы.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StatsAggregationService {

    private final StoreDailyStatisticsRepository storeDailyRepo;
    private final PostalServiceDailyStatisticsRepository postalDailyRepo;

    private final StoreWeeklyStatisticsRepository storeWeeklyRepo;
    private final StoreMonthlyStatisticsRepository storeMonthlyRepo;
    private final StoreYearlyStatisticsRepository storeYearlyRepo;

    private final PostalServiceWeeklyStatisticsRepository psWeeklyRepo;
    private final PostalServiceMonthlyStatisticsRepository psMonthlyRepo;
    private final PostalServiceYearlyStatisticsRepository psYearlyRepo;

    /**
     * Агрегирует статистику за предыдущий день.
     */
    @Scheduled(cron = "0 0 2 * * *", zone = "UTC")
    public void aggregateYesterday() {
        LocalDate yesterday = LocalDate.now(ZoneOffset.UTC).minusDays(1);
        aggregateForDate(yesterday);
    }

    /**
     * Проходит по каждому дню в указанном диапазоне и агрегирует статистику.
     * Операция идемпотентна и может безопасно выполняться повторно для одного и того же периода.
     *
     * @param from дата начала (включительно)
     * @param to   дата окончания (включительно)
     */
    @Transactional
    public void aggregateForRange(LocalDate from, LocalDate to) {
        LocalDate current = from;
        // Проходим по каждой дате и вызываем ежедневную агрегацию
        while (!current.isAfter(to)) {
            aggregateForDate(current);
            current = current.plusDays(1);
        }
    }

    /**
     * Агрегирует статистику за указанную дату.
     *
     * @param date дата для агрегации
     */
    @Transactional
    public void aggregateForDate(LocalDate date) {
        log.info("\uD83D\uDCCA Агрегируем статистику за {}", date);
        List<StoreDailyStatistics> storeDaily = storeDailyRepo.findByDate(date);
        List<PostalServiceDailyStatistics> psDaily = postalDailyRepo.findByDate(date);
        for (StoreDailyStatistics d : storeDaily) {
            aggregateStore(d, date);
        }
        for (PostalServiceDailyStatistics d : psDaily) {
            aggregatePostalService(d, date);
        }
    }

    private void aggregateStore(StoreDailyStatistics d, LocalDate date) {
        int week = date.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
        int weekYear = date.get(IsoFields.WEEK_BASED_YEAR);
        int month = date.getMonthValue();
        int year = date.getYear();
        Store store = d.getStore();

        LocalDate weekStart = date.with(ChronoField.DAY_OF_WEEK, 1);
        LocalDate weekEnd = date.with(ChronoField.DAY_OF_WEEK, 7);
        LocalDate monthStart = date.withDayOfMonth(1);
        LocalDate monthEnd = monthStart.plusMonths(1).minusDays(1);
        LocalDate yearStart = date.withDayOfYear(1);
        LocalDate yearEnd = yearStart.plusYears(1).minusDays(1);

        List<StoreDailyStatistics> weekStats =
                storeDailyRepo.findByStoreIdAndDateBetween(store.getId(), weekStart, weekEnd);
        List<StoreDailyStatistics> monthStats =
                storeDailyRepo.findByStoreIdAndDateBetween(store.getId(), monthStart, monthEnd);
        List<StoreDailyStatistics> yearStats =
                storeDailyRepo.findByStoreIdAndDateBetween(store.getId(), yearStart, yearEnd);

        StoreWeeklyStatistics weekly = storeWeeklyRepo
                .findByStoreIdAndPeriodYearAndPeriodNumber(store.getId(), weekYear, week)
                .orElseGet(() -> {
                    StoreWeeklyStatistics s = new StoreWeeklyStatistics();
                    s.setStore(store);
                    s.setPeriodYear(weekYear);
                    s.setPeriodNumber(week);
                    return s;
                });
        setStoreValues(weekly, weekStats);
        storeWeeklyRepo.save(weekly);

        StoreMonthlyStatistics monthly = storeMonthlyRepo
                .findByStoreIdAndPeriodYearAndPeriodNumber(store.getId(), year, month)
                .orElseGet(() -> {
                    StoreMonthlyStatistics s = new StoreMonthlyStatistics();
                    s.setStore(store);
                    s.setPeriodYear(year);
                    s.setPeriodNumber(month);
                    return s;
                });
        setStoreValues(monthly, monthStats);
        storeMonthlyRepo.save(monthly);

        StoreYearlyStatistics yearly = storeYearlyRepo
                .findByStoreIdAndPeriodYearAndPeriodNumber(store.getId(), year, 1)
                .orElseGet(() -> {
                    StoreYearlyStatistics s = new StoreYearlyStatistics();
                    s.setStore(store);
                    s.setPeriodYear(year);
                    s.setPeriodNumber(1);
                    return s;
                });
        setStoreValues(yearly, yearStats);
        storeYearlyRepo.save(yearly);
    }

    private void aggregatePostalService(PostalServiceDailyStatistics d, LocalDate date) {
        int week = date.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
        int weekYear = date.get(IsoFields.WEEK_BASED_YEAR);
        int month = date.getMonthValue();
        int year = date.getYear();
        Store store = d.getStore();
        PostalServiceType type = d.getPostalServiceType();

        LocalDate weekStart = date.with(ChronoField.DAY_OF_WEEK, 1);
        LocalDate weekEnd = date.with(ChronoField.DAY_OF_WEEK, 7);
        LocalDate monthStart = date.withDayOfMonth(1);
        LocalDate monthEnd = monthStart.plusMonths(1).minusDays(1);
        LocalDate yearStart = date.withDayOfYear(1);
        LocalDate yearEnd = yearStart.plusYears(1).minusDays(1);

        List<PostalServiceDailyStatistics> weekStats = postalDailyRepo
                .findByStoreIdAndPostalServiceTypeAndDateBetween(store.getId(), type, weekStart, weekEnd);
        List<PostalServiceDailyStatistics> monthStats = postalDailyRepo
                .findByStoreIdAndPostalServiceTypeAndDateBetween(store.getId(), type, monthStart, monthEnd);
        List<PostalServiceDailyStatistics> yearStats = postalDailyRepo
                .findByStoreIdAndPostalServiceTypeAndDateBetween(store.getId(), type, yearStart, yearEnd);

        PostalServiceWeeklyStatistics weekly = psWeeklyRepo
                .findByStoreIdAndPostalServiceTypeAndPeriodYearAndPeriodNumber(store.getId(), type, weekYear, week)
                .orElseGet(() -> {
                    PostalServiceWeeklyStatistics s = new PostalServiceWeeklyStatistics();
                    s.setStore(store);
                    s.setPostalServiceType(type);
                    s.setPeriodYear(weekYear);
                    s.setPeriodNumber(week);
                    return s;
                });
        setPsValues(weekly, weekStats);
        psWeeklyRepo.save(weekly);

        PostalServiceMonthlyStatistics monthly = psMonthlyRepo
                .findByStoreIdAndPostalServiceTypeAndPeriodYearAndPeriodNumber(store.getId(), type, year, month)
                .orElseGet(() -> {
                    PostalServiceMonthlyStatistics s = new PostalServiceMonthlyStatistics();
                    s.setStore(store);
                    s.setPostalServiceType(type);
                    s.setPeriodYear(year);
                    s.setPeriodNumber(month);
                    return s;
                });
        setPsValues(monthly, monthStats);
        psMonthlyRepo.save(monthly);

        PostalServiceYearlyStatistics yearly = psYearlyRepo
                .findByStoreIdAndPostalServiceTypeAndPeriodYearAndPeriodNumber(store.getId(), type, year, 1)
                .orElseGet(() -> {
                    PostalServiceYearlyStatistics s = new PostalServiceYearlyStatistics();
                    s.setStore(store);
                    s.setPostalServiceType(type);
                    s.setPeriodYear(year);
                    s.setPeriodNumber(1);
                    return s;
                });
        setPsValues(yearly, yearStats);
        psYearlyRepo.save(yearly);
    }

    private void setStoreValues(Aggregatable target, List<StoreDailyStatistics> stats) {
        Totals totals = sumStore(stats);
        applyStats(target, totals);
    }

    private void setPsValues(Aggregatable target, List<PostalServiceDailyStatistics> stats) {
        Totals totals = sumPostal(stats);
        applyStats(target, totals);
    }

    private void applyStats(Aggregatable target, Totals totals) {
        target.setSent(totals.sent);
        target.setDelivered(totals.delivered);
        target.setReturned(totals.returned);
        target.setSumDeliveryDays(totals.sumDeliveryDays);
        target.setSumPickupDays(totals.sumPickupDays);
        target.setUpdatedAt(ZonedDateTime.now(ZoneOffset.UTC));
    }

    private Totals sumStore(List<StoreDailyStatistics> stats) {
        Totals t = new Totals();
        for (StoreDailyStatistics s : stats) {
            t.sent += s.getSent();
            t.delivered += s.getDelivered();
            t.returned += s.getReturned();
            t.sumDeliveryDays = t.sumDeliveryDays.add(s.getSumDeliveryDays());
            t.sumPickupDays = t.sumPickupDays.add(s.getSumPickupDays());
        }
        return t;
    }

    private Totals sumPostal(List<PostalServiceDailyStatistics> stats) {
        Totals t = new Totals();
        for (PostalServiceDailyStatistics s : stats) {
            t.sent += s.getSent();
            t.delivered += s.getDelivered();
            t.returned += s.getReturned();
            t.sumDeliveryDays = t.sumDeliveryDays.add(s.getSumDeliveryDays());
            t.sumPickupDays = t.sumPickupDays.add(s.getSumPickupDays());
        }
        return t;
    }

    private static class Totals {
        int sent;
        int delivered;
        int returned;
        BigDecimal sumDeliveryDays = BigDecimal.ZERO;
        BigDecimal sumPickupDays = BigDecimal.ZERO;
    }
}
