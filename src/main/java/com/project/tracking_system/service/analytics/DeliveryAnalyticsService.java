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

        return List.of(new DeliveryFullPeriodStatsDTO(
                "Всего",
                aggregate.getTotalSent(),
                aggregate.getTotalDelivered(),
                aggregate.getTotalReturned()
        ));
    }

}