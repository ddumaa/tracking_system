package com.project.tracking_system.maper;

import com.project.tracking_system.dto.TrackInfoDTO;
import com.project.tracking_system.dto.TrackInfoListDTO;
import com.project.tracking_system.model.evropost.jsonResponseModel.JsonEvroTrackingResponse;
import lombok.extern.slf4j.Slf4j;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.stream.Collectors;

/**
 * Маппер для преобразования объекта {@link JsonEvroTrackingResponse} в объект {@link TrackInfoListDTO}.
 * <p>
 * Этот компонент используется для преобразования данных о статусах посылок, полученных от EuroPost,
 * в формат, пригодный для передачи в бизнес-логику приложения.
 * </p>
 *
 * @author Dmitriy Anisimov
 * @date 07.01.2025
 */
@Slf4j
@Component
public class JsonEvroTrackingResponseMapper {

    private final ModelMapper modelMapper;

    @Autowired
    public JsonEvroTrackingResponseMapper(ModelMapper modelMapper) {
        this.modelMapper = modelMapper;
    }

    /**
     * Преобразует объект {@link JsonEvroTrackingResponse} в объект {@link TrackInfoListDTO}.
     * <p>
     * Метод извлекает таблицу данных из ответа и преобразует каждый элемент в объект {@link TrackInfoDTO}.
     * </p>
     *
     * @param response Объект {@link JsonEvroTrackingResponse}, содержащий данные отслеживания посылки.
     * @return {@link TrackInfoListDTO}, содержащий список информации о статусах посылки.
     */
    public TrackInfoListDTO mapJsonEvroTrackingResponseToDTO(JsonEvroTrackingResponse response) {
        TrackInfoListDTO dto = new TrackInfoListDTO();
        dto.setList(response.getTable().stream()
                .map(jsonEvroTracking -> modelMapper.map(jsonEvroTracking, TrackInfoDTO.class))
                .collect(Collectors.toList()));
        log.info("✅ Маппинг завершён: {} записей", dto.getList().size());
        return dto;
    }
}