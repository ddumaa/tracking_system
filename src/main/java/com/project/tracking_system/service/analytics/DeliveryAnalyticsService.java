package com.project.tracking_system.service.analytics;

import com.project.tracking_system.dto.DeliveryFullPeriodStatsDTO;
import com.project.tracking_system.repository.DeliveryHistoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;

/**
 * @author Dmitriy Anisimov
 * @date 22.03.2025
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DeliveryAnalyticsService {

    private final DeliveryHistoryRepository deliveryHistoryRepository;

    public List<DeliveryFullPeriodStatsDTO> getFullPeriodStats(List<Long> storeIds,
                                                               ChronoUnit interval,
                                                               ZonedDateTime from,
                                                               ZonedDateTime to,
                                                               ZoneId userZone) {

        String intervalStr = switch (interval) {
            case DAYS -> "day";
            case WEEKS -> "week";
            case MONTHS -> "month";
            default -> throw new IllegalArgumentException("Unsupported interval: " + interval);
        };

        Timestamp fromTs = Timestamp.from(from.toInstant());
        Timestamp toTs = Timestamp.from(to.toInstant());

        List<Object[]> rows = deliveryHistoryRepository.getSentDeliveredReturnedGroupedByPeriod(
                storeIds, intervalStr, fromTs, toTs
        );

        DateTimeFormatter formatter = switch (interval) {
            case DAYS -> DateTimeFormatter.ofPattern("dd.MM");
            case WEEKS -> DateTimeFormatter.ofPattern("'Неделя' w");
            case MONTHS -> DateTimeFormatter.ofPattern("LLLL yyyy", new Locale("ru"));
            default -> DateTimeFormatter.ISO_DATE;
        };

        return rows.stream()
                .map(row -> {
                    Timestamp ts = (Timestamp) row[0];
                    ZonedDateTime period = ts.toInstant().atZone(userZone);
                    long sent = ((Number) row[1]).longValue();
                    long delivered = ((Number) row[2]).longValue();
                    long returned = ((Number) row[3]).longValue();

                    return new DeliveryFullPeriodStatsDTO(
                            formatter.format(period),
                            sent,
                            delivered,
                            returned
                    );
                })
                .toList();
    }

}