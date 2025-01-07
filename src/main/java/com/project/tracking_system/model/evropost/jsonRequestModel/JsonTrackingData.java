package com.project.tracking_system.model.evropost.jsonRequestModel;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

/**
 * Класс, представляющий данные для отслеживания посылки в системе EuroPost.
 * <p>
 * Этот класс используется для передачи информации о номере посылки, которую необходимо отслеживать.
 * </p>
 *
 * @author Dmitriy Anisimov
 * @date 07.01.2025
 */
@Getter
@Setter
public class JsonTrackingData extends JsonDataAbstract{

    @JsonProperty("Number")
    private String number;
    public JsonTrackingData(String number) {
        this.number = number;
    }

}