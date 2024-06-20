package com.project.tracking_system.service.JsonService;

import com.fasterxml.jackson.databind.JsonNode;
import com.project.tracking_system.model.jsonRequestModel.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;


@Service
public class GetJwtTokenService {

    private final JsonHandlerService jsonHandlerService;
    private final JsonPacket jsonPacket;
    private final RequestFactory requestFactory;

    @Autowired
    public GetJwtTokenService(RequestFactory requestFactory, JsonHandlerService jsonHandlerService, JsonPacket jsonPacket) {
        this.requestFactory = requestFactory;
        this.jsonHandlerService = jsonHandlerService;
        this.jsonPacket = jsonPacket;

    }

    public void getJwtToken() {

        JsonRequest jsonRequest = requestFactory.createGetJWTRequest();
        JsonNode jsonNode = jsonHandlerService.jsonRequest(jsonRequest);

        JsonNode jwtNode = jsonNode.path("Table").path(0).path("JWT");
        if (jwtNode.isMissingNode()) {
            throw new RuntimeException("Токен JWT не найден в ответе");
        }

        jsonPacket.setJwt(jwtNode.asText());
    }
}