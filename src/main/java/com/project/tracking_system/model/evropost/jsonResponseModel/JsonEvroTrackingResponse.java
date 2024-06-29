package com.project.tracking_system.model.evropost.jsonResponseModel;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@JsonIgnoreProperties(ignoreUnknown = true)
@Data
public class JsonEvroTrackingResponse {

    @JsonProperty("Table")
    private List<JsonEvroTracking> table;
}
