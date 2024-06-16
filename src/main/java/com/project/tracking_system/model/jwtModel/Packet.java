package com.project.tracking_system.model.jwtModel;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@Data
@AllArgsConstructor
@NoArgsConstructor
public class Packet {

    @JsonProperty("JWT")
    private String JWT;
    @JsonProperty("MethodName")
    private String MethodName;
    @JsonProperty("ServiceNumber")
    private String ServiceNumber;
    @JsonProperty("Data")
    private JwtData Data;

}
