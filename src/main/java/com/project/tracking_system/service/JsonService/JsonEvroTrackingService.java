package com.project.tracking_system.service.JsonService;

import com.fasterxml.jackson.databind.JsonNode;
import com.project.tracking_system.model.jsonRequestModel.JsonData;
import com.project.tracking_system.model.jsonRequestModel.JsonRequest;
import com.project.tracking_system.model.jsonRequestModel.JsonMethodName;
import com.project.tracking_system.model.jsonRequestModel.JsonPacket;
import com.project.tracking_system.model.jsonResponseModel.JsonEvroTrackingResponse;
import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;


@Service
public class JsonEvroTrackingService {

    private final JsonPacket packet;
    private final JsonHandlerService jsonHandlerService;
    private final Jsonb jsonb;

    @Autowired
    public JsonEvroTrackingService(JsonPacket packet, JsonHandlerService jsonHandlerService, Jsonb jsonb) {
        this.packet = packet;
        this.jsonHandlerService = jsonHandlerService;
        this.jsonb = jsonb;
    }

    public JsonEvroTrackingResponse getJson(String number) {

        String methodeName = JsonMethodName.POSTAL_TRACKING.toString();

        JsonRequest jsonRequest = new JsonRequest(
                "",
                new JsonPacket(
                        packet.getJWT(),
                        methodeName,
                        packet.getServiceNumber(),
                        new JsonData(
                                number
                        )
                )
        );

        JsonNode jsonNode = jsonHandlerService.jsonRequest(jsonRequest);
        JsonNode tableNode = jsonNode.path("Table");
        try {
            JsonEvroTrackingResponse response = jsonb.fromJson(tableNode.toString(), JsonEvroTrackingResponse.class);
            return response;
        } catch (JsonbException e) {
            throw new RuntimeException("Ошибка десериализации ответа JSON.", e);
        }
    }
}

