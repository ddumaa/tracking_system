package com.project.tracking_system.service.JsonService;

import com.fasterxml.jackson.databind.JsonNode;
import com.project.tracking_system.model.jsonRequestModel.JsonData;
import com.project.tracking_system.model.jsonRequestModel.JsonRequest;
import com.project.tracking_system.model.jsonRequestModel.JsonMethodName;
import com.project.tracking_system.model.jsonRequestModel.JsonPacket;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;


@Service
public class GetJwtTokenService {

    private final JsonPacket packet;
    private final JsonData jsonData;
    private final JsonHandlerService jsonHandlerService;
    private final JsonPacket jsonPacket;

    @Autowired
    public GetJwtTokenService(JsonPacket packet, JsonData jsonData, JsonHandlerService jsonHandlerService, JsonPacket jsonPacket) {
        this.packet = packet;
        this.jsonData = jsonData;
        this.jsonHandlerService = jsonHandlerService;
        this.jsonPacket = jsonPacket;
    }

    public String getJwtToken() {

        String methodeName = JsonMethodName.GET_JWT.toString();

        JsonRequest jsonRequest = new JsonRequest(
                "",
                new JsonPacket(
                        packet.getJWT(),
                        methodeName,
                        packet.getServiceNumber(),
                        new JsonData(
                                jsonData.getLoginName(),
                                jsonData.getPassword(),
                                jsonData.getLoginNameTypeId()
                        )
                )
        );

        JsonNode jsonNode = jsonHandlerService.jsonRequest(jsonRequest);

        JsonNode jwtNode = jsonNode.path("Table").path(0).path("JWT");
        if (jwtNode.isMissingNode()) {
            throw new RuntimeException("Токен JWT не найден в ответе");
        }

        jsonPacket.setJWT(jwtNode.asText());
        return jsonPacket.getJWT();
    }
}