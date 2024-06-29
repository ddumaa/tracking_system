package com.project.tracking_system.maper;

import com.project.tracking_system.dto.TrackInfoDTO;
import com.project.tracking_system.dto.TrackInfoListDTO;
import com.project.tracking_system.model.evropost.jsonResponseModel.JsonEvroTrackingResponse;
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

    public TrackInfoListDTO mapJsonEvroTrackingResponseToDTO(JsonEvroTrackingResponse response) {
        TrackInfoListDTO dto = new TrackInfoListDTO();
        dto.setList(response.getTable().stream()
                .map(jsonEvroTracking -> modelMapper.map(jsonEvroTracking, TrackInfoDTO.class))
                .collect(Collectors.toList()));
        return dto;
    }
}
