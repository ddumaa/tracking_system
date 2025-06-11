package com.project.tracking_system.mapper;

import com.project.tracking_system.dto.TrackInfoDTO;
import com.project.tracking_system.dto.TrackInfoListDTO;
import com.project.tracking_system.model.evropost.jsonResponseModel.JsonEvroTracking;
import com.project.tracking_system.model.evropost.jsonResponseModel.JsonEvroTrackingResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * Маппер для преобразования ответа EuroPost в DTO.
 */
@Mapper(componentModel = "spring")
public interface JsonEvroTrackingResponseMapper {

    /**
     * Преобразует {@link JsonEvroTrackingResponse} в {@link TrackInfoListDTO}.
     *
     * @param response ответ от EuroPost
     * @return список данных о статусах
     */
    @Mapping(target = "list", source = "table")
    TrackInfoListDTO mapJsonEvroTrackingResponseToDTO(JsonEvroTrackingResponse response);

    /**
     * Преобразует единичный элемент ответа в DTO.
     *
     * @param tracking модель ответа
     * @return DTO со статусом
     */
    TrackInfoDTO mapJsonEvroTrackingToDTO(JsonEvroTracking tracking);
}
