package com.project.tracking_system.service.analytics;

import com.project.tracking_system.entity.*;
import com.project.tracking_system.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.IsoFields;
import java.util.List;

/**
 * Aggregates daily statistics into weekly, monthly and yearly tables.
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
     * Aggregates statistics for the previous day.
     */
    @Scheduled(cron = "0 0 2 * * *", zone = "UTC")
    public void aggregateYesterday() {
        LocalDate yesterday = LocalDate.now(ZoneOffset.UTC).minusDays(1);
        aggregateForDate(yesterday);
    }

    /**
     * Aggregates statistics for the given date.
     *
     * @param date date to aggregate
     */
    @Transactional
    public void aggregateForDate(LocalDate date) {
        log.info("\uD83D\uDCCA Aggregating statistics for {}", date);
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

        StoreWeeklyStatistics weekly = storeWeeklyRepo
                .findByStoreIdAndPeriodYearAndPeriodNumber(store.getId(), weekYear, week)
                .orElseGet(() -> {
                    StoreWeeklyStatistics s = new StoreWeeklyStatistics();
                    s.setStore(store);
                    s.setPeriodYear(weekYear);
                    s.setPeriodNumber(week);
                    return s;
                });
        addStoreValues(weekly, d);
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
        addStoreValues(monthly, d);
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
        addStoreValues(yearly, d);
        storeYearlyRepo.save(yearly);
    }

    private void aggregatePostalService(PostalServiceDailyStatistics d, LocalDate date) {
        int week = date.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
        int weekYear = date.get(IsoFields.WEEK_BASED_YEAR);
        int month = date.getMonthValue();
        int year = date.getYear();
        Store store = d.getStore();
        PostalServiceType type = d.getPostalServiceType();

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
        addPsValues(weekly, d);
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
        addPsValues(monthly, d);
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
        addPsValues(yearly, d);
        psYearlyRepo.save(yearly);
    }

    private void addStoreValues(StoreWeeklyStatistics target, StoreDailyStatistics d) {
        target.setSent(target.getSent() + d.getSent());
        target.setDelivered(target.getDelivered() + d.getDelivered());
        target.setReturned(target.getReturned() + d.getReturned());
        target.setSumDeliveryDays(target.getSumDeliveryDays().add(d.getSumDeliveryDays()));
        target.setSumPickupDays(target.getSumPickupDays().add(d.getSumPickupDays()));
        target.setUpdatedAt(ZonedDateTime.now(ZoneOffset.UTC));
    }

    private void addStoreValues(StoreMonthlyStatistics target, StoreDailyStatistics d) {
        target.setSent(target.getSent() + d.getSent());
        target.setDelivered(target.getDelivered() + d.getDelivered());
        target.setReturned(target.getReturned() + d.getReturned());
        target.setSumDeliveryDays(target.getSumDeliveryDays().add(d.getSumDeliveryDays()));
        target.setSumPickupDays(target.getSumPickupDays().add(d.getSumPickupDays()));
        target.setUpdatedAt(ZonedDateTime.now(ZoneOffset.UTC));
    }

    private void addStoreValues(StoreYearlyStatistics target, StoreDailyStatistics d) {
        target.setSent(target.getSent() + d.getSent());
        target.setDelivered(target.getDelivered() + d.getDelivered());
        target.setReturned(target.getReturned() + d.getReturned());
        target.setSumDeliveryDays(target.getSumDeliveryDays().add(d.getSumDeliveryDays()));
        target.setSumPickupDays(target.getSumPickupDays().add(d.getSumPickupDays()));
        target.setUpdatedAt(ZonedDateTime.now(ZoneOffset.UTC));
    }

    private void addPsValues(PostalServiceWeeklyStatistics target, PostalServiceDailyStatistics d) {
        target.setSent(target.getSent() + d.getSent());
        target.setDelivered(target.getDelivered() + d.getDelivered());
        target.setReturned(target.getReturned() + d.getReturned());
        target.setSumDeliveryDays(target.getSumDeliveryDays().add(d.getSumDeliveryDays()));
        target.setSumPickupDays(target.getSumPickupDays().add(d.getSumPickupDays()));
        target.setUpdatedAt(ZonedDateTime.now(ZoneOffset.UTC));
    }

    private void addPsValues(PostalServiceMonthlyStatistics target, PostalServiceDailyStatistics d) {
        target.setSent(target.getSent() + d.getSent());
        target.setDelivered(target.getDelivered() + d.getDelivered());
        target.setReturned(target.getReturned() + d.getReturned());
        target.setSumDeliveryDays(target.getSumDeliveryDays().add(d.getSumDeliveryDays()));
        target.setSumPickupDays(target.getSumPickupDays().add(d.getSumPickupDays()));
        target.setUpdatedAt(ZonedDateTime.now(ZoneOffset.UTC));
    }

    private void addPsValues(PostalServiceYearlyStatistics target, PostalServiceDailyStatistics d) {
        target.setSent(target.getSent() + d.getSent());
        target.setDelivered(target.getDelivered() + d.getDelivered());
        target.setReturned(target.getReturned() + d.getReturned());
        target.setSumDeliveryDays(target.getSumDeliveryDays().add(d.getSumDeliveryDays()));
        target.setSumPickupDays(target.getSumPickupDays().add(d.getSumPickupDays()));
        target.setUpdatedAt(ZonedDateTime.now(ZoneOffset.UTC));
    }
}
