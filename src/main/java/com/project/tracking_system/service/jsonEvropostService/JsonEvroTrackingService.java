package com.project.tracking_system.service.jsonEvropostService;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.tracking_system.model.evropost.jsonRequestModel.JsonRequest;
import com.project.tracking_system.model.evropost.jsonResponseModel.JsonEvroTracking;
import com.project.tracking_system.model.evropost.jsonResponseModel.JsonEvroTrackingResponse;
import jakarta.json.bind.JsonbException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Сервис для получения информации о трекинге посылки от ЕвроПочты.
 * <p>
 * Этот сервис выполняет запрос для получения данных о трекинге посылки по номеру и десериализует
 * ответ в объект {@link JsonEvroTrackingResponse}, содержащий список объектов {@link JsonEvroTracking}.
 * </p>
 *
 * @author Dmitriy Anisimov
 * @date 07.01.2025
 */
@Service
public class JsonEvroTrackingService {

    private final JsonHandlerService jsonHandlerService;
    private final RequestFactory requestFactory;

    @Autowired
    public JsonEvroTrackingService(JsonHandlerService jsonHandlerService, RequestFactory requestFactory) {
        this.jsonHandlerService = jsonHandlerService;
        this.requestFactory = requestFactory;
    }

    /**
     * Получает информацию о трекинге посылки от ЕвроПочты.
     * <p>
     * Метод выполняет запрос к внешнему API для получения данных о трекинге посылки по номеру,
     * а затем десериализует ответ в объект {@link JsonEvroTrackingResponse}.
     * </p>
     *
     * @param number Номер посылки, для которой требуется получить информацию о трекинге.
     * @return {@link JsonEvroTrackingResponse} объект с данными о трекинге.
     * @throws RuntimeException если произошла ошибка десериализации JSON.
     */
    public JsonEvroTrackingResponse getJson(String number) {

        JsonRequest jsonRequest = requestFactory.createTrackingRequest(number);
        JsonNode jsonNode = jsonHandlerService.jsonRequest(jsonRequest);

        JsonEvroTrackingResponse response = new JsonEvroTrackingResponse();
        JsonNode tableNode = jsonNode.path("Table");
        try {
            ObjectMapper mapper = new ObjectMapper();
            List<JsonEvroTracking> table = mapper.convertValue(tableNode,
                    new TypeReference<List<JsonEvroTracking>>() {});
            response.setTable(table);
            return response;
        } catch (JsonbException e) {
            throw new RuntimeException("Ошибка десериализации ответа JSON.", e);
        }
    }
}