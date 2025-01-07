package com.project.tracking_system.model.evropost.jsonResponseModel;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Модель для представления JSON-ответа от сервиса EuroPost.
 * <p>
 * Эта модель используется для сериализации и десериализации данных, получаемых от сервиса отслеживания посылок EuroPost.
 * </p>
 *
 * @author Dmitriy Anisimov
 * @date 07.01.2025
 */
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