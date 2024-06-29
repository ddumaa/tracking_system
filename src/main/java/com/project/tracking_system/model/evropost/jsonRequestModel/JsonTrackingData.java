package com.project.tracking_system.model.evropost.jsonRequestModel;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class JsonTrackingData extends JsonDataAbstract{

//    @JsonProperty("PostalItemExternalId")
//    private String postalItemExternalId;

    @JsonProperty("Number")
    private String number;

    public JsonTrackingData(String number) {
        this.number = number;
    }

//    public JsonTrackingData(String number, String postalItemExternalId) {
//        this.number = number;
//        this.postalItemExternalId = postalItemExternalId;
//    }

}
