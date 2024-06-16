package com.project.tracking_system.model.jsonResponseModel;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.stereotype.Component;

@JsonIgnoreProperties(ignoreUnknown = true)
@Data
@AllArgsConstructor
@NoArgsConstructor
@Component
public class JsonEvroTracking {

    private String Timex;
    private String InfoTrack;
    private String IsChooseDeliveryTime;
    private String CheckxFrom;
    private String CheckxTo;
    private String Info;

}
