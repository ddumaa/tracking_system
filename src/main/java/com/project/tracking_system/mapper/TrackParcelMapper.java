package com.project.tracking_system.mapper;

import com.project.tracking_system.dto.TrackParcelDTO;
import com.project.tracking_system.entity.TrackParcel;
import org.mapstruct.Context;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Маппер для преобразования сущности {@link TrackParcel} в DTO.
 */
@Mapper(componentModel = "spring")
public interface TrackParcelMapper {

    /**
     * Преобразует сущность посылки в DTO.
     *
     * @param parcel   сущность посылки
     * @param userZone часовой пояс пользователя
     * @return DTO с информацией о посылке
     */
    @Mapping(target = "status", source = "status.description")
    @Mapping(target = "iconHtml", source = "status.iconHtml")
    @Mapping(target = "data", source = "data", qualifiedByName = "formatDate")
    @Mapping(target = "storeId", source = "store.id")
    TrackParcelDTO toDto(TrackParcel parcel, @Context ZoneId userZone);

    /**
     * Форматирует дату с учётом часового пояса пользователя.
     */
    @Named("formatDate")
    default String formatDate(ZonedDateTime date, @Context ZoneId userZone) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss")
                .withZone(userZone);
        return formatter.format(date);
    }
}
