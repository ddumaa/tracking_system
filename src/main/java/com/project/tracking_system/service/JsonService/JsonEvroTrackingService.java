package com.project.tracking_system.service.JsonService;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.tracking_system.model.jsonRequestModel.*;
import com.project.tracking_system.model.jsonResponseModel.JsonEvroTracking;
import com.project.tracking_system.model.jsonResponseModel.JsonEvroTrackingResponse;
import jakarta.json.bind.JsonbException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;


@Service
public class JsonEvroTrackingService {

    private final JsonHandlerService jsonHandlerService;
    private final RequestFactory requestFactory;

    @Autowired
    public JsonEvroTrackingService(JsonHandlerService jsonHandlerService, RequestFactory requestFactory) {
        this.jsonHandlerService = jsonHandlerService;
        this.requestFactory = requestFactory;
    }

    public JsonEvroTrackingResponse getJson(String number) {

        JsonRequest jsonRequest = requestFactory.createTrackingRequest(number);
        JsonNode jsonNode = jsonHandlerService.jsonRequest(jsonRequest);

        JsonEvroTrackingResponse response = new JsonEvroTrackingResponse();
        JsonNode tableNode = jsonNode.path("Table");
        try {
            ObjectMapper mapper = new ObjectMapper();
            List<JsonEvroTracking> table = mapper.convertValue(tableNode, new TypeReference<List<JsonEvroTracking>>() {});
            response.setTable(table);
            return response;
        } catch (JsonbException e) {
            throw new RuntimeException("Ошибка десериализации ответа JSON.", e);
        }
    }
}

