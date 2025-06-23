package com.project.tracking_system.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import java.util.ArrayList;
import java.util.List;

@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class TrackInfoListDTO {

    private List<TrackInfoDTO> list = new ArrayList<>();

    public void addTrackInfo(TrackInfoDTO trackInfoDTO) {
        list.add(trackInfoDTO);
    }

}
