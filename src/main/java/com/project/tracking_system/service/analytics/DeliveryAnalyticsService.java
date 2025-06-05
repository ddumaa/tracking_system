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

        List<StoreStatistics> stats = storeAnalyticsRepository.findAllById(storeIds);
        StoreStatistics aggregate = storeAnalyticsService.aggregateStatistics(stats);

        ZonedDateTime current = from;
        List<DeliveryFullPeriodStatsDTO> list = new java.util.ArrayList<>();

        while (!current.isAfter(to)) {
            String label = switch (interval) {
                case DAYS -> current.toLocalDate().toString();
                case WEEKS -> "Week " + current.get(java.time.temporal.IsoFields.WEEK_OF_WEEK_BASED_YEAR);
                case MONTHS -> current.getMonth() + " " + current.getYear();
                default -> current.toLocalDate().toString();
            };

            list.add(new DeliveryFullPeriodStatsDTO(
                    label,
                    aggregate.getTotalSent(),
                    aggregate.getTotalDelivered(),
                    aggregate.getTotalReturned()
            ));

            current = switch (interval) {
                case DAYS -> current.plusDays(1);
                case WEEKS -> current.plusWeeks(1);
                case MONTHS -> current.plusMonths(1);
                default -> current.plusDays(1);
            };
        }

        return list;
    }

}