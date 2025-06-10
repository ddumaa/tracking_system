package com.project.tracking_system.service.analytics;

import com.project.tracking_system.dto.PeriodStatsDTO;
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

    private final PeriodDataResolver periodDataResolver;

    /**
     * Агрегирует статистику доставок для указанных магазинов за заданный период.
     * <p>
     * Ежедневная статистика группируется по переданному {@link ChronoUnit} и суммируется.
     * Метод всегда возвращает DTO для каждого интервала между {@code from} и {@code to}.
     * </p>
     *
     * @param storeIds список идентификаторов магазинов
     * @param interval интервал группировки (дни, недели, месяцы, годы)
     * @param from     дата начала в часовом поясе пользователя
     * @param to       дата окончания в часовом поясе пользователя
     * @param userZone часовой пояс пользователя
     * @return список агрегированной статистики, упорядоченный по периоду
     */
    public List<PeriodStatsDTO> getFullPeriodStats(List<Long> storeIds,
                                                   ChronoUnit interval,
                                                   ZonedDateTime from,
                                                   ZonedDateTime to,
                                                   ZoneId userZone) {
        // Delegate to resolver which chooses optimal data source
        return periodDataResolver.resolve(storeIds, interval, from, to, userZone);
    }

}