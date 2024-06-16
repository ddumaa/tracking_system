package com.project.tracking_system.model.jsonResponseModel;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
@Data
public class JsonEvroTrackingResponse {
    private List<JsonEvroTracking> Table;
}
