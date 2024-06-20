package com.project.tracking_system.model.jsonRequestModel;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@Data
@AllArgsConstructor
@NoArgsConstructor
public class JsonPacket {

    @JsonProperty("JWT")
    private String jwt;

    @JsonProperty("MethodName")
    private String methodName;

    @Value("${evro.jwt.ServiceNumber}")
    @JsonProperty("ServiceNumber")
    private String serviceNumber;

    @JsonProperty("Data")
    private JsonDataAbstract data;

}

