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
    private Long id;
    private String number;
    private String status;
    private String timestamp;
    private transient String iconHtml;
    private Long storeId;

    public TrackParcelDTO(TrackParcel trackParcel, ZoneId userZone) {
        this.id = trackParcel.getId();
        this.number = trackParcel.getNumber();
        this.status = trackParcel.getStatus().getDescription();
        this.storeId = trackParcel.getStore().getId();
        this.timestamp = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss")
                .withZone(userZone)
                .format(trackParcel.getTimestamp());
    }
}