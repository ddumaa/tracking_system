package com.project.tracking_system.service.analytics;

import com.project.tracking_system.dto.PostalServiceStatsDTO;
import com.project.tracking_system.entity.PostalServiceStatistics;
import com.project.tracking_system.entity.PostalServiceType;
import com.project.tracking_system.repository.PostalServiceStatisticsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class PostalServiceStatisticsService {

    private final PostalServiceStatisticsRepository repository;

    /**
     * Возвращает статистику по всем службам доставки одного магазина.
     * <p>
     * Репозиторий выдаёт отдельную запись {@link PostalServiceStatistics}
     * для каждой службы доставки. В этих записях уже содержатся накопленные
     * счётчики, которые копируются в {@link PostalServiceStatsDTO} через
     * {@link #mapToDto(PostalServiceStatistics)}.
     * </p>
     *
     * @param storeId идентификатор магазина
     * @return список статистик по службам доставки магазина
     *         (службы без отправлений исключаются)
     */
    @Transactional(readOnly = true)
    public List<PostalServiceStatsDTO> getStatsByStore(Long storeId) {
        return repository.findByStoreId(storeId)
                .stream()
                .filter(stat -> stat.getPostalServiceType() != PostalServiceType.UNKNOWN)
                .filter(stat -> stat.getTotalSent() > 0)
                .map(this::mapToDto)
                .toList();
    }

    /**
     * Агрегирует статистику по нескольким магазинам.
     * <p>
     * Сначала из репозитория загружаются все записи
     * {@link PostalServiceStatistics} для указанных магазинов. Далее они
     * объединяются по типу службы доставки: счётчики суммируются методом
     * {@link #mergeStats(PostalServiceStatistics, PostalServiceStatistics)}.
     * Полученные агрегированные сущности затем конвертируются в DTO.
     * </p>
     *
     * @param storeIds список идентификаторов магазинов
     * @return агрегированная статистика по службам доставки
     *         (службы без отправлений исключаются)
     */
    @Transactional(readOnly = true)
    public List<PostalServiceStatsDTO> getStatsForStores(List<Long> storeIds) {
        List<PostalServiceStatistics> stats = repository.findByStoreIdIn(storeIds);
        Map<PostalServiceType, PostalServiceStatistics> aggregated = new EnumMap<>(PostalServiceType.class);
        for (PostalServiceStatistics stat : stats) {
            if (stat.getPostalServiceType() == PostalServiceType.UNKNOWN) {
                continue;
            }
            aggregated.merge(stat.getPostalServiceType(), stat, this::mergeStats);
        }
        return aggregated.values().stream()
                .filter(stat -> stat.getTotalSent() > 0)
                .map(this::mapToDto)
                .toList();
    }

    private PostalServiceStatistics mergeStats(PostalServiceStatistics a, PostalServiceStatistics b) {
        a.setTotalSent(a.getTotalSent() + b.getTotalSent());
        a.setTotalDelivered(a.getTotalDelivered() + b.getTotalDelivered());
        a.setTotalReturned(a.getTotalReturned() + b.getTotalReturned());
        a.setSumDeliveryDays(a.getSumDeliveryDays().add(b.getSumDeliveryDays()));
        a.setSumPickupDays(a.getSumPickupDays().add(b.getSumPickupDays()));
        return a;
    }

    private PostalServiceStatsDTO mapToDto(PostalServiceStatistics stats) {
        PostalServiceStatsDTO dto = new PostalServiceStatsDTO();
        dto.setPostalService(stats.getPostalServiceType().getDisplayName());
        dto.setSent(stats.getTotalSent());
        dto.setDelivered(stats.getTotalDelivered());
        dto.setReturned(stats.getTotalReturned());
        dto.setSumDeliveryDays(stats.getSumDeliveryDays().doubleValue());
        dto.setSumPickupTimeDays(stats.getSumPickupDays().doubleValue());
        return dto;
    }
}
