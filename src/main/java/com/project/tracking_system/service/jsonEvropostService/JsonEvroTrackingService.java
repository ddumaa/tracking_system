package com.project.tracking_system.service.jsonEvropostService;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.tracking_system.dto.ResolvedCredentialsDTO;
import com.project.tracking_system.entity.User;
import com.project.tracking_system.model.evropost.jsonRequestModel.JsonRequest;
import com.project.tracking_system.model.evropost.jsonResponseModel.JsonEvroTracking;
import com.project.tracking_system.model.evropost.jsonResponseModel.JsonEvroTrackingResponse;
import com.project.tracking_system.utils.UserCredentialsResolver;
import jakarta.json.bind.JsonbException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger logger = LoggerFactory.getLogger(JsonEvroTrackingService.class);

    private final JsonHandlerService jsonHandlerService;
    private final RequestFactory requestFactory;
    private final UserCredentialsResolver userCredentialsResolver;

    @Autowired
    public JsonEvroTrackingService(JsonHandlerService jsonHandlerService, RequestFactory requestFactory,
                                   UserCredentialsResolver userCredentialsResolver) {
        this.jsonHandlerService = jsonHandlerService;
        this.requestFactory = requestFactory;
        this.userCredentialsResolver = userCredentialsResolver;
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
    public JsonEvroTrackingResponse getJson(User user, String number) {
        logger.info("Запрос на получение данных для пользователя: {}, почтовый номер: {}", user.getEmail(), number);

        ResolvedCredentialsDTO credentials = userCredentialsResolver.resolveCredentials(user);
        logger.info("Данные для запроса определены. Используются {} данные.",
                user.getUseCustomCredentials() ? "пользовательские" : "системные");

        JsonRequest jsonRequest = requestFactory.createTrackingRequest(
                credentials.getJwt(),
                credentials.getServiceNumber(),
                number);
        logger.debug("Запрос создан. Почтовый номер: {}", number);

        JsonNode jsonNode;
        try {
            jsonNode = jsonHandlerService.jsonRequest(jsonRequest);
            logger.info("Запрос успешно выполнен для почтового номера: {}", number);
        } catch (Exception e) {
            logger.error("Ошибка при выполнении запроса для почтового номера: {}", number, e);
            throw new RuntimeException("Ошибка при выполнении запроса.", e);
        }

        JsonEvroTrackingResponse response = new JsonEvroTrackingResponse();
        JsonNode tableNode = jsonNode.path("Table");

        try {
            ObjectMapper mapper = new ObjectMapper();
            List<JsonEvroTracking> table = mapper.convertValue(tableNode,
                    new TypeReference<List<JsonEvroTracking>>() {});
            response.setTable(table);

            logger.info("Ответ успешно обработан. Количество записей: {}", table.size());
            return response;
        } catch (JsonbException e) {
            logger.error("Ошибка десериализации ответа JSON для почтового номера: {}", number, e);
            throw new RuntimeException("Ошибка десериализации ответа JSON.", e);
        }
    }
}