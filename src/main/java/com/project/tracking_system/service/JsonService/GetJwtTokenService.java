package com.project.tracking_system.service.JsonService;

import com.fasterxml.jackson.databind.JsonNode;
import com.project.tracking_system.model.jsonModel.JsonData;
import com.project.tracking_system.model.jsonModel.JsonRequest;
import com.project.tracking_system.model.JwtToken;
import com.project.tracking_system.model.jsonModel.MethodName;
import com.project.tracking_system.model.jsonModel.Packet;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;


@Service
public class GetJwtTokenService {

    private final JwtToken jwtToken;
    private final Packet packet;
    private final JsonData jsonData;
    private final JsonHandlerService jsonHandlerService;

    @Autowired
    public GetJwtTokenService(JwtToken jwtToken, Packet packet, JsonData jsonData, JsonHandlerService jsonHandlerService) {
        this.jwtToken = jwtToken;
        this.packet = packet;
        this.jsonData = jsonData;
        this.jsonHandlerService = jsonHandlerService;
    }

    public String getJwtToken() {

        String methodeName = MethodName.GET_JWT.toString();

        JsonRequest jsonRequest = new JsonRequest(
                "",
                new Packet(
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

        jwtToken.setToken(jwtNode.asText());
        return jwtToken.getToken();
    }
}