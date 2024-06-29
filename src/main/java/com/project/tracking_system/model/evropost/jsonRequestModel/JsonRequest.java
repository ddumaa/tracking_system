package com.project.tracking_system.model.evropost.jsonRequestModel;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@Data
@AllArgsConstructor
@NoArgsConstructor
public class JsonRequest {

    @JsonProperty("CRC")
    private String crc = "";
    @JsonProperty("Packet")
    private JsonPacket packet;

}
