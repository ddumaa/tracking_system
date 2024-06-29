package com.project.tracking_system.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@NoArgsConstructor
@AllArgsConstructor
@Data
public class TrackInfoListDTO {

    private List<TrackInfoDTO> list = new ArrayList<>();

    public void addTrackInfo(TrackInfoDTO trackInfoDTO) {
        list.add(trackInfoDTO);
    }

}
