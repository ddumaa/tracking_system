package com.project.tracking_system.service.analytics;

import com.project.tracking_system.dto.DeliveryFullPeriodStatsDTO;
import com.project.tracking_system.entity.StoreStatistics;
import com.project.tracking_system.repository.StoreAnalyticsRepository;
import com.project.tracking_system.repository.DeliveryHistoryRepository;
import com.project.tracking_system.entity.DeliveryHistory;
import com.project.tracking_system.service.analytics.StoreAnalyticsService;
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

    private final StoreAnalyticsRepository storeAnalyticsRepository;
    private final StoreAnalyticsService storeAnalyticsService;
    private final DeliveryHistoryRepository deliveryHistoryRepository;

    public List<DeliveryFullPeriodStatsDTO> getFullPeriodStats(List<Long> storeIds,
                                                               ChronoUnit interval,
                                                               ZonedDateTime from,
                                                               ZonedDateTime to,
                                                               ZoneId userZone) {

        ZonedDateTime start = alignToPeriod(from, interval, userZone);
        ZonedDateTime end   = alignToPeriod(to, interval, userZone);

        ZonedDateTime fromUtc = from.withZoneSameInstant(java.time.ZoneOffset.UTC);
        ZonedDateTime toUtc = to.withZoneSameInstant(java.time.ZoneOffset.UTC);

        // Подготавливаем контейнер для подсчёта статистики по каждому интервалу
        java.util.Map<ZonedDateTime, long[]> counters = new java.util.LinkedHashMap<>();
        ZonedDateTime cursor = start;
        while (!cursor.isAfter(end)) {
            counters.put(cursor, new long[]{0, 0, 0}); // sent, delivered, returned
            cursor = nextPeriod(cursor, interval);
        }

        // Загружаем события доставки из истории
        List<DeliveryHistory> sentEvents = deliveryHistoryRepository
                .findByStoreIdInAndSendDateBetween(storeIds, fromUtc, toUtc);
        List<DeliveryHistory> deliveredEvents = deliveryHistoryRepository
                .findByStoreIdInAndReceivedDateBetween(storeIds, fromUtc, toUtc);
        List<DeliveryHistory> returnedEvents = deliveryHistoryRepository
                .findByStoreIdInAndReturnedDateBetween(storeIds, fromUtc, toUtc);

        for (DeliveryHistory dh : sentEvents) {
            ZonedDateTime key = alignToPeriod(dh.getSendDate(), interval, userZone);
            long[] arr = counters.get(key);
            if (arr != null) arr[0]++;
        }
        for (DeliveryHistory dh : deliveredEvents) {
            ZonedDateTime key = alignToPeriod(dh.getReceivedDate(), interval, userZone);
            long[] arr = counters.get(key);
            if (arr != null) arr[1]++;
        }
        for (DeliveryHistory dh : returnedEvents) {
            ZonedDateTime key = alignToPeriod(dh.getReturnedDate(), interval, userZone);
            long[] arr = counters.get(key);
            if (arr != null) arr[2]++;
        }

        List<DeliveryFullPeriodStatsDTO> list = new java.util.ArrayList<>();
        for (java.util.Map.Entry<ZonedDateTime, long[]> entry : counters.entrySet()) {
            ZonedDateTime periodStart = entry.getKey();
            long[] arr = entry.getValue();
            String label = formatLabel(periodStart, interval);
            list.add(new DeliveryFullPeriodStatsDTO(label, arr[0], arr[1], arr[2]));
        }

        return list;
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

    private ZonedDateTime nextPeriod(ZonedDateTime current, ChronoUnit interval) {
        return switch (interval) {
            case DAYS -> current.plusDays(1);
            case WEEKS -> current.plusWeeks(1);
            case MONTHS -> current.plusMonths(1);
            default -> current.plusDays(1);
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