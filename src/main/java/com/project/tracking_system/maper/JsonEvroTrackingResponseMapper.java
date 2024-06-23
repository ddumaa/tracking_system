package com.project.tracking_system.maper;

import com.project.tracking_system.dto.EvroTrackInfoDTO;
import com.project.tracking_system.dto.EvroTrackInfoListDTO;
import com.project.tracking_system.model.jsonResponseModel.JsonEvroTrackingResponse;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.stream.Collectors;

@Component
public class JsonEvroTrackingResponseMapper {

    private final ModelMapper modelMapper;

    @Autowired
    public JsonEvroTrackingResponseMapper(ModelMapper modelMapper) {
        this.modelMapper = modelMapper;
    }

    public EvroTrackInfoListDTO mapJsonEvroTrackingResponseToDTO(JsonEvroTrackingResponse response) {
        EvroTrackInfoListDTO dto = new EvroTrackInfoListDTO();
        dto.setEvroTrackInfoDTOList(response.getTable().stream()
                .map(jsonEvroTracking -> modelMapper.map(jsonEvroTracking, EvroTrackInfoDTO.class))
                .collect(Collectors.toList()));
        return dto;
    }
}
