package com.project.tracking_system.dto;

import com.project.tracking_system.entity.TrackParcel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@NoArgsConstructor
@AllArgsConstructor
@Data
public class TrackParcelDTO {

    private String number;
    private String status;
    private String data;
    private transient String iconHtml;

    public TrackParcelDTO(TrackParcel trackParcel) {
        this.number = trackParcel.getNumber();
        this.status = trackParcel.getStatus().getDescription();
        this.data = trackParcel.getData();
    }

}