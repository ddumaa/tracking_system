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
    private String timex;

    @JsonProperty("InfoTrack")
    private String infoTrack;

    @JsonProperty("IsChooseDeliveryTime")
    private String isChooseDeliveryTime;

    @JsonProperty("CheckxFrom")
    private String checkxFrom;

    @JsonProperty("CheckxTo")
    private String checkxTo;

    @JsonProperty("Info")
    private String info;

    @JsonCreator
    public JsonEvroTracking(@JsonProperty("Timex") String timex,
                            @JsonProperty("InfoTrack") String infoTrack,
                            @JsonProperty("IsChooseDeliveryTime") String isChooseDeliveryTime,
                            @JsonProperty("CheckxFrom") String checkxFrom,
                            @JsonProperty("CheckxTo") String checkxTo,
                            @JsonProperty("Info") String info) {
        this.timex = timex;
        this.infoTrack = infoTrack;
        this.isChooseDeliveryTime = isChooseDeliveryTime;
        this.checkxFrom = checkxFrom;
        this.checkxTo = checkxTo;
        this.info = info;
    }

}
