package com.project.tracking_system.model.jsonResponseModel;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.stereotype.Component;

@JsonIgnoreProperties(ignoreUnknown = true)
@Data
@NoArgsConstructor
@Component
public class JsonEvroTracking {

    @JsonProperty("Timex")
    private String Timex;

    @JsonProperty("InfoTrack")
    private String InfoTrack;

    @JsonProperty("IsChooseDeliveryTime")
    private String IsChooseDeliveryTime;

    @JsonProperty("CheckxFrom")
    private String CheckxFrom;

    @JsonProperty("CheckxTo")
    private String CheckxTo;

    @JsonProperty("Info")
    private String Info;

    @JsonCreator
    public JsonEvroTracking(@JsonProperty("Timex") String timex,
                            @JsonProperty("InfoTrack") String infoTrack,
                            @JsonProperty("IsChooseDeliveryTime") String isChooseDeliveryTime,
                            @JsonProperty("CheckxFrom") String checkxFrom,
                            @JsonProperty("CheckxTo") String checkxTo,
                            @JsonProperty("Info") String info) {
        this.Timex = timex;
        this.InfoTrack = infoTrack;
        this.IsChooseDeliveryTime = isChooseDeliveryTime;
        this.CheckxFrom = checkxFrom;
        this.CheckxTo = checkxTo;
        this.Info = info;
    }

}
