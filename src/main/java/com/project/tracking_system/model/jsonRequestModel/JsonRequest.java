package com.project.tracking_system.model.jsonRequestModel;

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
    private String CRC = "";
    @JsonProperty("Packet")
    private JsonPacket Packet;

    public JsonRequest(String CRC, JsonPacket packet) {
        this.CRC = CRC;
        this.Packet = packet;
    }

}
