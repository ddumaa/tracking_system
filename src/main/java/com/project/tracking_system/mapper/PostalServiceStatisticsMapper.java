package com.project.tracking_system.mapper;

import com.project.tracking_system.dto.PostalServiceStatsDTO;
import com.project.tracking_system.entity.PostalServiceStatistics;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * Маппер для преобразования статистики почтовых служб в DTO.
 */
@Mapper(componentModel = "spring")
public interface PostalServiceStatisticsMapper {

    /**
     * Конвертирует сущность статистики в DTO.
     *
     * @param stats сущность статистики
     * @return объект DTO
     */
    @Mapping(target = "serviceName", source = "postalServiceType.displayName")
    @Mapping(target = "sent", source = "totalSent")
    @Mapping(target = "delivered", source = "totalDelivered")
    @Mapping(target = "returned", source = "totalReturned")
    @Mapping(target = "avgDeliveryDays", expression = "java(stats.getAverageDeliveryDays().longValue())")
    @Mapping(target = "avgPickupTimeDays", expression = "java(stats.getAveragePickupDays().longValue())")
    PostalServiceStatsDTO toDto(PostalServiceStatistics stats);
}
