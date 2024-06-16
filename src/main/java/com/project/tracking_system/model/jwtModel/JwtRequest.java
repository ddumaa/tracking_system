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
public class JwtRequest {

    @JsonProperty("CRC")
    private String CRC;
    @JsonProperty("Packet")
    private Packet Packet;

}