package com.project.tracking_system.dto;

import com.project.tracking_system.entity.TrackParcel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

@Component
@NoArgsConstructor
@AllArgsConstructor
@Data
public class TrackParcelDTO {
    private String number;
    private String status;
    private String data;
    private transient String iconHtml;
    private Long storeId;

    public TrackParcelDTO(TrackParcel trackParcel, ZoneId userZone) {
        this.number = trackParcel.getNumber();
        this.status = trackParcel.getStatus().getDescription();
        this.storeId = trackParcel.getStore().getId();
        this.data = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss")
                .withZone(userZone)
                .format(trackParcel.getData());
    }
}