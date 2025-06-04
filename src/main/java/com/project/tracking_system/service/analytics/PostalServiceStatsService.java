package com.project.tracking_system.service.analytics;

import com.project.tracking_system.dto.PostalServiceStatsDTO;
import com.project.tracking_system.entity.PostalServiceType;
import com.project.tracking_system.repository.DeliveryHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PostalServiceStatsService {

    private final DeliveryHistoryRepository deliveryHistoryRepository;

    public List<PostalServiceStatsDTO> getStatsByStore(Long storeId) {
        List<Object[]> raw = deliveryHistoryRepository.getRawStatsByPostalService(storeId);
        return raw.stream()
                .map(this::mapToDto)
                .toList();
    }

    public List<PostalServiceStatsDTO> getStatsForStores(List<Long> storeIds) {
        List<Object[]> raw = deliveryHistoryRepository.getRawStatsByPostalServiceForStores(storeIds);
        return raw.stream()
                .map(this::mapToDto)
                .toList();
    }

    private PostalServiceStatsDTO mapToDto(Object[] row) {
        String code = (String) row[0];
        PostalServiceType type = PostalServiceType.fromCode(code);
        String displayName = type.getDisplayName();

        int sent = row[1] != null ? ((Number) row[1]).intValue() : 0;
        int delivered = row[2] != null ? ((Number) row[2]).intValue() : 0;
        int returned = row[3] != null ? ((Number) row[3]).intValue() : 0;
        double avgDeliveryDays = row[4] != null ? ((Number) row[4]).doubleValue() : 0.0;
        double avgPickupTimeDays = row[5] != null ? ((Number) row[5]).doubleValue() : 0.0;

        return new PostalServiceStatsDTO(
                displayName,
                sent,
                delivered,
                returned,
                avgDeliveryDays,
                avgPickupTimeDays
        );
    }
}
