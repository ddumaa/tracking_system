package com.project.tracking_system.service.analytics;

import com.project.tracking_system.dto.PostalServiceStatsDTO;
import com.project.tracking_system.entity.StoreStatistics;
import com.project.tracking_system.repository.StoreAnalyticsRepository;
import com.project.tracking_system.service.analytics.StoreAnalyticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PostalServiceStatsService {

    private final StoreAnalyticsRepository storeAnalyticsRepository;
    private final StoreAnalyticsService storeAnalyticsService;

    public List<PostalServiceStatsDTO> getStatsByStore(Long storeId) {
        StoreStatistics stats = storeAnalyticsRepository.findByStoreId(storeId)
                .orElseThrow(() -> new IllegalArgumentException("Статистика не найдена"));
        return List.of(mapToDto(stats));
    }

    public List<PostalServiceStatsDTO> getStatsForStores(List<Long> storeIds) {
        List<StoreStatistics> stats = storeAnalyticsRepository.findAllById(storeIds);
        StoreStatistics aggregate = storeAnalyticsService.aggregateStatistics(stats);
        return List.of(mapToDto(aggregate));
    }

    private PostalServiceStatsDTO mapToDto(StoreStatistics stats) {
        return new PostalServiceStatsDTO(
                "Все службы",
                stats.getTotalSent(),
                stats.getTotalDelivered(),
                stats.getTotalReturned(),
                stats.getAverageDeliveryDays().doubleValue(),
                stats.getAveragePickupDays().doubleValue()
        );
    }
}
